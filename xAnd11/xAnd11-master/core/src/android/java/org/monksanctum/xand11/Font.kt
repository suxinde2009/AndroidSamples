// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.monksanctum.xand11.core

import android.graphics.Paint
import android.graphics.Typeface
import org.monksanctum.xand11.atoms.AtomManager
import org.monksanctum.xand11.core.Platform.Companion.intToHexString
import org.monksanctum.xand11.core.Utils.toCard16
import org.monksanctum.xand11.fonts.FontSpec
import org.monksanctum.xand11.graphics.XDrawable

actual class Font actual internal constructor(private val mSpec: FontSpec, name: String?) {

    actual val fontProperties: MutableList<FontProperty> = ArrayList()
    val mChars: MutableList<CharInfo> = ArrayList()

    actual var minBounds = CharInfo()
    actual var maxBounds = CharInfo()

    actual val minCharOrByte2: Char = 32.toChar() // Card16
    actual val defaultChar: Char = 32.toChar() // Card16
    actual var maxCharOrByte2: Char = 255.toChar() // Card16

    actual var isRtl: Boolean = false

    actual val minByte1: Byte = 0
    actual val maxByte1: Byte = 0

    actual var allCharsExist: Boolean = false

    actual var fontAscent: Int = 0 // Int16
    actual var fontDescent: Int = 0 // Int16

    val paint: Paint

    //        Platform.logd("Font", "getChars");
    //        ArrayList<CharInfo> chars = new ArrayList();
    //        char[] bytes = new char[maxCharOrByte2 - minCharOrByte2];
    //        float[] widths = new float[bytes.length];
    //        Rect bounds = new Rect();
    //        for (int i = 0; i < bytes.length; i++) {
    //            bytes[i] = (char) (i + minCharOrByte2);
    //        }
    //        mPaint.getTextWidths(new String(bytes), widths);
    //        for (int i = 0; i < bytes.length; i++) {
    //            CharInfo info = new CharInfo();
    //            float width = widths[i];
    //            mPaint.getTextBounds(bytes, i, 1, bounds);
    //            info.leftSideBearing = toCard16(bounds.left);
    //            info.rightSideBearing = toCard16(bounds.right);
    //            info.characterWidth = (int) width;
    //            info.ascent = toCard16(-bounds.top);
    //            info.descent = toCard16(bounds.bottom);
    //            chars.add(info);
    //        }
    //        Platform.logd("Font", "Done");
    actual val chars: List<CharInfo>
        get() = mChars

    init {
        paint = Paint()
        // TODO: Add default and fixed.
        if (mSpec.getSpec(0) == DEFAULT) {
            paint.typeface = Typeface.DEFAULT
        } else if (mSpec.getSpec(0) == FIXED) {
            paint.typeface = Typeface.MONOSPACE
        } else {
            var base = Typeface.DEFAULT
            var style = Typeface.NORMAL

            if (FontSpec.WEIGHT_BOLD == mSpec.getSpec(FontSpec.WEIGHT_NAME)) {
                style = style or Typeface.BOLD
            }
            if (FontSpec.SLANT_ITALICS == mSpec.getSpec(FontSpec.SLANT)) {
                style = style or Typeface.ITALIC
            }

            try {
                val sizeStr = mSpec.getSpec(FontSpec.PIXEL_SIZE)
                val n = java.lang.Float.valueOf(sizeStr)

                if (n > 0) {
                    paint.textSize = n * FONT_SCALING
                }
            } catch (e: java.lang.NumberFormatException) {
            }

            val type = mSpec.getSpec(FontSpec.FAMILY_NAME)
            if (FontSpec.SPACING_PROPORTIONAL != mSpec.getSpec(FontSpec.SPACING)) {
                base = Typeface.MONOSPACE
            } else if (FontSpec.FAMILY_DEFAULT == type) {
                base = Typeface.DEFAULT
            } else if (FontSpec.FAMILY_SERIF == type) {
                base = Typeface.SERIF
            } else if (FontSpec.FAMILY_SANS_SERIF == type) {
                base = Typeface.SANS_SERIF
            } else {
                base = Typeface.create(type, style)
            }
            if (FontSpec.REGISTRY_10646 == mSpec.getSpec(FontSpec.CHARSET_REGISTRY)) {
                maxCharOrByte2 = 65534.toChar()
            }

            paint.typeface = Typeface.create(base, style)
        }

        // Calculate the minimum and maximum widths.
        val bytes = CharArray(255 - minCharOrByte2.toInt() + 1)
        val widths = FloatArray(bytes.size)

        for (i in bytes.indices) {
            bytes[i] = (i + minCharOrByte2.toInt()).toChar()
        }

        val metrics = paint.fontMetricsInt

        fontAscent = (-metrics.ascent).toShort().toInt()
        fontDescent = metrics.descent.toShort().toInt()
        maxBounds.ascent = (-metrics.top).toShort().toInt()
        maxBounds.descent = metrics.bottom.toShort().toInt()

        paint.getTextWidths(String(bytes), widths)

        val bounds = Rect()
        minBounds.characterWidth = Int.MAX_VALUE
        for (i in widths.indices) {
            val width = widths[i]
            if (width < minBounds.characterWidth) {
                minBounds.characterWidth = width.toInt()
            }
            if (width > maxBounds.characterWidth) {
                maxBounds.characterWidth = width.toInt()
            }
            // TODO: Don't hold this stuff in memory, seems wasteful.
            val info = CharInfo()
            paint.getTextBounds(bytes, i, 1, bounds)
            info.leftSideBearing = toCard16(bounds.left)
            info.rightSideBearing = toCard16(bounds.right)
            info.characterWidth = width.toInt()
            info.ascent = toCard16(-bounds.top)
            info.descent = toCard16(bounds.bottom)
            mChars.add(info)
        }
        maxBounds.rightSideBearing = maxBounds.characterWidth

        if (name != null) {
            val nameProp = FontProperty()
            nameProp.name = AtomManager.instance.internAtom("FONT")
            nameProp.value = AtomManager.instance.internAtom(name)
            fontProperties.add(nameProp)
        }
    }

    actual fun measureText(s: String): Float {
        return paint.measureText(s)
    }

    actual fun paintGetTextBounds(text: String, start: Int, length: Int, bounds: Rect) {
        return paint.getTextBounds(text, start, length, bounds)
    }

    override fun toString(): String {
        return mSpec.toString()
    }

    actual fun getTextBounds(str: String, x: Int, y: Int, rect: Rect) {
        rect.left = x
        rect.right = x + paint.measureText(str).toInt()
        rect.top = y - maxBounds.ascent
        rect.bottom = y + maxBounds.descent
    }

    actual fun drawText(drawable: XDrawable, context: GraphicsContext,
                        str: String, x: Int, y: Int, rect: Rect) {
        val paint = paint
        paint.style = Paint.Style.FILL
        paint.color = context.background
        val canvas = drawable.lockCanvas(context)
        canvas.drawRect(rect, paint)

        paint.color = context.foreground
        canvas.drawText(str, x.toFloat(), y.toFloat(), paint)
        if (DEBUG) Platform.logd("DrawingProtocol", "Drawing text " + x + " " + y + " \""
                + str + "\" " + intToHexString(context.foreground))
        drawable.unlockCanvas()
    }

    actual class FontProperty {
        actual var name: Int = 0 // Atom
        actual var value: Int = 0
    }

    actual class CharInfo {
        actual var leftSideBearing: Int = 0 // Int16
        actual var rightSideBearing: Int = 0 // Int16
        actual var characterWidth: Int = 0 // Int16
        actual var ascent: Int = 0 // Int16
        actual var descent: Int = 0 // Int16
        actual var attributes: Int = 0 // Card16
    }

    actual companion object {

        actual val LEFT_TO_RIGHT: Byte = 0
        actual val RIGHT_TO_LEFT: Byte = 1

        actual internal val DEFAULT = "cursor"
        actual internal val FIXED = "fixed"
        private val FONT_SCALING = 2.5f

        fun getTextBounds(str: String, paint: Paint, x: Int, y: Int, rect: Rect) {
            paint.getTextBounds(str, 0, str.length, rect)
            rect.offset(x, y)
        }
    }

}
