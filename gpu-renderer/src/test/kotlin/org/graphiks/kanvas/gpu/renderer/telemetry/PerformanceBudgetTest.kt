package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformanceBudgetTest {

    @Test
    fun `evaluate returns pass when fps meets threshold`() {
        val result = PerformanceBudgetEvaluator().evaluate("rect", 60f, 30f)
        assertEquals("pass", result.status)
        assertEquals("rect", result.familyName)
        assertEquals(30f, result.targetFps)
    }

    @Test
    fun `evaluate returns warning when fps is below threshold but above half`() {
        val result = PerformanceBudgetEvaluator().evaluate("rrect", 20f, 30f)
        assertEquals("warning", result.status)
        assertEquals(15f, result.warningFps)
    }

    @Test
    fun `evaluate returns fail when fps is below half threshold`() {
        val result = PerformanceBudgetEvaluator().evaluate("path", 10f, 30f)
        assertEquals("fail", result.status)
    }

    @Test
    fun `evaluate computes measuredMs from fps`() {
        val result = PerformanceBudgetEvaluator().evaluate("text", 60f, 30f)
        assertEquals(1000f / 60f, result.measuredMs)
    }

    @Test
    fun `evaluate uses thresholdFps as targetFps`() {
        val result = PerformanceBudgetEvaluator().evaluate("image", 60f, 60f)
        assertEquals(60f, result.targetFps)
    }

    @Test
    fun `PerFamilyBudget defaults to unknown status and zero measuredMs`() {
        val budget = PerFamilyBudget(familyName = "test", targetFps = 60f, warningFps = 30f)
        assertEquals("unknown", budget.status)
        assertEquals(0f, budget.measuredMs)
    }

    @Test
    fun `evaluate with zero fps returns fail`() {
        val result = PerformanceBudgetEvaluator().evaluate("rect", 0f, 30f)
        assertEquals("fail", result.status)
    }
}
