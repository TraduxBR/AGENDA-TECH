package com.filestech.agenda_tech.ui.screens.about

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The fill of the thank-you note's folded hands, brought close to WHITE with its inner strokes kept pale (as in
 * SMS Tech and Notes Tech). The matrix is applied here by hand, the way Android applies it:
 * R' = m0·R + m1·G + m2·B + m3·A + m4, components and offset in 0-255.
 */
class TowardsWhiteTest {

    private val floor = 0.72f

    private fun apply(colour: FloatArray): FloatArray {
        val m = towardsWhite(floor).values
        val in255 = FloatArray(4) { colour[it] * 255f }
        return FloatArray(4) { row ->
            (0 until 4).sumOf { column -> (m[row * 5 + column] * in255[column]).toDouble() }.toFloat() +
                m[row * 5 + 4]
        }.map { it / 255f }.toFloatArray()
    }

    @Test
    fun `white stays white`() {
        val r = apply(floatArrayOf(1f, 1f, 1f, 1f))
        for (i in 0 until 3) assertThat(r[i]).isWithin(EPS).of(1f)
    }

    @Test
    fun `black becomes the floor, a light grey, never darker`() {
        val r = apply(floatArrayOf(0f, 0f, 0f, 1f))
        for (i in 0 until 3) assertThat(r[i]).isWithin(EPS).of(floor)
    }

    @Test
    fun `no colour of its own is left, and the lighter stays lighter`() {
        val red = apply(floatArrayOf(1f, 0f, 0f, 1f))
        assertThat(red[0]).isWithin(EPS).of(red[1])
        assertThat(red[1]).isWithin(EPS).of(red[2])
        val yellow = apply(floatArrayOf(1f, 0.85f, 0.3f, 1f))
        val orange = apply(floatArrayOf(0.9f, 0.5f, 0.1f, 1f))
        assertThat(yellow[0]).isGreaterThan(orange[0])
    }

    @Test
    fun `alpha is kept as is`() {
        assertThat(apply(floatArrayOf(1f, 0.3f, 0.2f, 0.35f))[3]).isWithin(EPS).of(0.35f)
    }

    private companion object {
        const val EPS = 1e-3f
    }
}
