# SwiftDrop Concept B assets

The SVG exports mirror the production VectorDrawable geometry. PNG exports at
24, 48, 96, 192 and 512 pixels are generated from this geometry for review and
store artwork; production does not use a raster-generated icon.

The two arrows occupy the centered 52dp artboard inside the adaptive 108dp layer.
Their furthest points remain inside Android's 66dp guaranteed visible circle.
The adaptive background is royal blue; the upper arrow is white and the lower
arrow cyan. The monochrome foreground uses the same silhouette in a single tint.
No Samsung or Telegram assets are included.

Flat in-app and store exports optically enlarge the same arrow paths. The adaptive
foreground remains inside its safe zone; no path geometry or arrow weight changes.
