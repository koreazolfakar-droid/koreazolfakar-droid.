package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UltraWireTest {
    private fun <T> socketPair(
        secretHost: ByteArray,
        secretSender: ByteArray = secretHost,
        host: (UltraWire.Channel) -> T,
        sender: (UltraWire.Channel) -> Unit
    ): T {
        ServerSocket(0,1).use { listening ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<T> {
                    listening.accept().use { incoming ->
                        incoming.soTimeout = 5_000
                        host(UltraWire.host(incoming.getInputStream(), incoming.getOutputStream(),secretHost))
                    }
                }
                Socket("127.0.0.1",listening.localPort).use { outbound ->
                    outbound.soTimeout = 5_000
                    sender(UltraWire.client(outbound.getInputStream(), outbound.getOutputStream(),secretSender))
                }
                return future.get(8,TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun encryptedFramesAndBidirectionalAcknowledgements() {
        val key = UltraWire.newSecret()
        val reply = socketPair(key,host={ c ->
            val frame=c.read()
            assertEquals(19,frame.type)
            assertEquals("test",frame.data.decodeToString())
            c.send(25,"accepted".toByteArray())
            42
        },sender={ c ->
            c.send(19,"test".toByteArray())
            val frame=c.read()
            assertEquals(25,frame.type)
            assertEquals("accepted",frame.data.decodeToString())
        })
        assertEquals(42,reply)
    }

    @Test fun verifiedStreamingFileAndExactLength() {
        val key = UltraWire.newSecret()
        val original = ByteArray(500_123).also(SecureRandom()::nextBytes)
        val received = ByteArrayOutputStream()
        val hostResult = socketPair(key,host={ c ->
            UltraFileWire.receive(c,
                allow={ it.name=="sample.bin" && it.bytes==original.size.toLong() },
                createOutput={ received },
                commit={ },
                rollback={ received.reset() })
        },sender={ c ->
            val ok = UltraFileWire.send(c,UltraFileWire.Offer("sample.bin",original.size.toLong()),
                original.inputStream())
            assertTrue(ok)
        })
        assertTrue(hostResult)
        assertArrayEquals(original,received.toByteArray())
    }

    @Test fun denialReturnsFalseAndNeverCreatesFile() {
        val key = UltraWire.newSecret()
        val result = socketPair(key,host={ c ->
            UltraFileWire.receive(c,allow={false},createOutput={ fail("No write after denial");ByteArrayOutputStream() },
                commit={ fail("No commit") },rollback={ })
        },sender={ c ->
            assertFalse(UltraFileWire.send(c,UltraFileWire.Offer("private.txt",0),byteArrayOf().inputStream()))
        })
        assertFalse(result)
    }

    @Test fun incorrectQrSecretCannotAuthenticate() {
        val hostKey = UltraWire.newSecret()
        val senderKey = UltraWire.newSecret()
        try {
            socketPair(hostKey,senderKey,
                host={ c -> c.read() },
                sender={ c -> c.send(5) })
            fail("Authentication must reject unrelated secrets")
        } catch (expected: Exception) {
            assertTrue(expected is SecurityException || expected.cause is SecurityException ||
                expected is IOException)
        }
    }

    @Test fun rejectsInvalidMetadataAndOversizedFrames() {
        val key=UltraWire.newSecret()
        socketPair(key,host={ c ->
            val frame=c.read()
            assertEquals(18,frame.type)
            assertArrayEquals(byteArrayOf(1,2,3),frame.data)
        },sender={ c ->
            assertThrows(IllegalArgumentException::class.java) { c.send(2,ByteArray(UltraWire.CHUNK+1)) }
            c.send(18,byteArrayOf(1,2,3))
        })
    }
}
