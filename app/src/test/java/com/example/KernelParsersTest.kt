package com.example

import com.example.data.KernelParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelParsersTest {

    @Test
    fun `parseKernelRelease detects 4_19_325 LTS correctly`() {
        val procVersion = "Linux version 4.19.325-perf+ (builder@host) (Android (7284624) clang version 12.0.5) #1 SMP PREEMPT"
        val release = KernelParsers.parseKernelRelease(procVersion)
        assertEquals("4.19.325-perf+", release)
        assertTrue(KernelParsers.isKernel419Series(release))
        assertTrue(KernelParsers.isExact419325(release))
    }

    @Test
    fun `parseBracketedSelection extracts active scheduler`() {
        val (active, all) = KernelParsers.parseBracketedSelection("noop deadline [cfq] bfq")
        assertEquals("cfq", active)
        assertEquals(listOf("noop", "deadline", "cfq", "bfq"), all)
    }

    @Test
    fun `parseProcStat computes CPU delta percentage`() {
        val t1 = KernelParsers.parseProcStat("cpu  1000 0 200 8800 0 0 0 0 0 0")
        val t2 = KernelParsers.parseProcStat("cpu  1500 0 300 9200 0 0 0 0 0 0")
        // total diff = 1000, idle diff = 400 -> 60% busy
        val pct = KernelParsers.computeCpuUsagePercent(t1["cpu"], t2["cpu"])
        assertEquals(60f, pct, 0.01f)
    }

    @Test
    fun `parseMemInfo parses total available and cached MB`() {
        val raw = """
            MemTotal:        4096000 kB
            MemFree:          512000 kB
            MemAvailable:    2048000 kB
            Buffers:          102400 kB
            Cached:          1024000 kB
            SwapTotal:       2097152 kB
            SwapFree:        1048576 kB
        """.trimIndent()
        val mem = KernelParsers.parseMemInfo(raw)
        assertEquals(4000L, mem.totalMb)
        assertEquals(2000L, mem.availableMb)
        assertEquals(2000L, mem.usedMb)
        assertEquals(1000L, mem.cachedMb)
        assertEquals(2048L, mem.swapTotalMb)
        assertEquals(1024L, mem.swapFreeMb)
    }

    @Test
    fun `parseWmSizeOutput and parseWmDensityOutput handle override`() {
        val sizeOut = "Physical size: 1080x2400\nOverride size: 720x1600"
        val (phys, ovr) = KernelParsers.parseWmSizeOutput(sizeOut)
        assertEquals("1080x2400", phys)
        assertEquals("720x1600", ovr)

        val densOut = "Physical density: 440\nOverride density: 380"
        val (pDens, oDens) = KernelParsers.parseWmDensityOutput(densOut)
        assertEquals(440, pDens)
        assertEquals(380, oDens)
        assertFalse(KernelParsers.isExact419325("5.10.198"))
    }
}
