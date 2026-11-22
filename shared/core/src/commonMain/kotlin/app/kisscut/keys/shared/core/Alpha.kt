package app.kisscut.keys.shared.core

/** True when any ARGB pixel is not fully opaque. Decides the cut-line look (docs/DATA_MODEL.md). */
fun hasTransparentPixel(argb: IntArray): Boolean = argb.any { (it ushr 24) != 0xFF }
