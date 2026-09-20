package fi.aalto.radio.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Programme loudness as ITU-R BS.1770 / EBU R128 measure it (LUFS): the two
 * K-weighting filters, 400 ms blocks, and the absolute (−70 LUFS) and
 * relative (−10 LU) gates, so silence and quiet talk do not drag the result
 * down. Pure arithmetic, no Android: tested on the JVM.
 *
 * Keeps at most [maxBlocks] blocks (10 minutes by default), so a long
 * listening session follows the station as it is now.
 */
internal class LoudnessMeter(
    val sampleRate: Int,
    val channels: Int,
    private val maxBlocks: Int = 1_500
) {
    // Stage 1: high shelf (head effect). Stage 2: high pass (RLB).
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private val ra1: Double
    private val ra2: Double

    init {
        var f0 = 1681.974450955533
        val g = 3.999843853973347
        var q = 0.7071752369554196
        var k = tan(PI * f0 / sampleRate)
        val vh = 10.0.pow(g / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        b0 = (vh + vb * k / q + k * k) / a0
        b1 = 2.0 * (k * k - vh) / a0
        b2 = (vh - vb * k / q + k * k) / a0
        a1 = 2.0 * (k * k - 1.0) / a0
        a2 = (1.0 - k / q + k * k) / a0

        f0 = 38.13547087602444
        q = 0.5003270373238773
        k = tan(PI * f0 / sampleRate)
        val d = 1.0 + k / q + k * k
        ra1 = 2.0 * (k * k - 1.0) / d
        ra2 = (1.0 - k / q + k * k) / d
    }

    // Filter state per channel: stage 1 x1 x2 y1 y2, stage 2 x1 x2 y1 y2.
    private val state = Array(channels) { DoubleArray(8) }
    private val blockFrames = (sampleRate * 0.4).toInt().coerceAtLeast(1)
    private var blockSum = 0.0
    private var blockFrameCount = 0
    private var channelIndex = 0
    private var frameSum = 0.0
    private val blocks = ArrayDeque<Double>()

    /** One interleaved sample, −1…1. Channels in order; frames follow each other. */
    fun add(sample: Double) {
        val s = state[channelIndex]
        val y1 = b0 * sample + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3]
        s[1] = s[0]; s[0] = sample; s[3] = s[2]; s[2] = y1
        val y2 = y1 - 2.0 * s[4] + s[5] - ra1 * s[6] - ra2 * s[7]
        s[5] = s[4]; s[4] = y1; s[7] = s[6]; s[6] = y2
        frameSum += y2 * y2

        channelIndex++
        if (channelIndex == channels) {
            channelIndex = 0
            blockSum += frameSum
            frameSum = 0.0
            blockFrameCount++
            if (blockFrameCount == blockFrames) {
                blocks.addLast(blockSum / blockFrames)
                if (blocks.size > maxBlocks) blocks.removeFirst()
                blockSum = 0.0
                blockFrameCount = 0
            }
        }
    }

    /** Seconds of audio above the absolute gate: how much the result rests on. */
    val gatedSeconds: Double
        get() = blocks.count { loudness(it) > ABSOLUTE_GATE } * 0.4

    /** Integrated loudness in LUFS, or null before anything audible was heard. */
    fun integratedLufs(): Double? {
        val audible = blocks.filter { loudness(it) > ABSOLUTE_GATE }
        if (audible.isEmpty()) return null
        val relativeGate = loudness(audible.average()) - 10.0
        val gated = audible.filter { loudness(it) > relativeGate }
        if (gated.isEmpty()) return null
        return loudness(gated.average())
    }

    companion object {
        const val ABSOLUTE_GATE = -70.0

        fun loudness(power: Double): Double =
            if (power <= 0.0) Double.NEGATIVE_INFINITY else -0.691 + 10.0 * log10(power)

        fun power(lufs: Double): Double = 10.0.pow((lufs + 0.691) / 10.0)
    }
}
