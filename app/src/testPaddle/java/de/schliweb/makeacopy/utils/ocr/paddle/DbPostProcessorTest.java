/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** JVM-Unit-Tests für {@link DbPostProcessor}. */
public class DbPostProcessorTest {

    private static float[][] makeProb(int w, int h) {
        return new float[h][w];
    }

    private static void fillRect(float[][] prob, int x0, int y0, int w, int h, float v) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                prob[y][x] = v;
            }
        }
    }

    @Test
    public void twoSeparateRectangles_yieldExactlyTwoQuads() {
        // Probmap mit zwei klar getrennten 8x4 Rechtecken (mehr als 16 Pixel Lücke).
        // Verwende Default-Schwellwerte (db=0.3, box=0.6) -> Probabilities = 0.9
        // unclipRatio=1.0 für deterministische Bbox-Prüfung.
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.0);
        float[][] prob = makeProb(64, 32);
        fillRect(prob, 4, 4, 10, 6, 0.9f); // Rect A
        fillRect(prob, 40, 20, 12, 8, 0.9f); // Rect B

        List<Quad> quads = pp.process(prob);
        assertEquals("Exakt zwei Komponenten", 2, quads.size());

        // Beide Quads liegen innerhalb der Eingabegrenzen.
        for (Quad q : quads) {
            assertTrue(q.minX() >= 0);
            assertTrue(q.minY() >= 0);
            assertTrue(q.maxX() <= 64);
            assertTrue(q.maxY() <= 32);
        }

        // Mindestens ein Quad enthält das Zentrum von A bzw. B.
        boolean hitA = false, hitB = false;
        for (Quad q : quads) {
            if (containsPoint(q, 9.0, 7.0)) hitA = true;
            if (containsPoint(q, 46.0, 24.0)) hitB = true;
        }
        assertTrue("Quad enthält Zentrum von Rect A", hitA);
        assertTrue("Quad enthält Zentrum von Rect B", hitB);
    }

    @Test
    public void allBelowBoxThresh_yieldsEmptyList() {
        // Probabilities knapp über db_thresh, aber unter box_thresh -> Komponente wird verworfen.
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.6);
        float[][] prob = makeProb(32, 16);
        fillRect(prob, 4, 4, 10, 6, 0.4f);

        List<Quad> quads = pp.process(prob);
        assertTrue("Erwartet leere Liste, war: " + quads.size(), quads.isEmpty());
    }

    @Test
    public void emptyProbMap_returnsEmpty() {
        DbPostProcessor pp = new DbPostProcessor();
        assertTrue(pp.process(new float[16][32]).isEmpty());
    }

    @Test
    public void quadCornersInTlTrBrBlOrder() {
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.0);
        float[][] prob = makeProb(32, 16);
        fillRect(prob, 4, 4, 10, 6, 0.9f);
        List<Quad> quads = pp.process(prob);
        assertEquals(1, quads.size());
        Quad q = quads.get(0);
        // TL.x <= TR.x, BL.x <= BR.x; TL.y <= BL.y, TR.y <= BR.y
        assertTrue(q.x[0] <= q.x[1]);
        assertTrue(q.x[3] <= q.x[2]);
        assertTrue(q.y[0] <= q.y[3]);
        assertTrue(q.y[1] <= q.y[2]);
    }

    private static boolean containsPoint(Quad q, double px, double py) {
        return px >= q.minX() && px <= q.maxX() && py >= q.minY() && py <= q.maxY();
    }

    @Test
    public void stripeFilter_dropsFlatHomogeneousStripe() {
        // Schmaler 1500×12-Streifen mit homogener Probability (~CMYK-Farbbalken).
        // Aspect = 125 > 25, jede Zeile vollständig gefüllt → Variance = 0 → wird verworfen.
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.0);
        float[][] prob = makeProb(1600, 32);
        fillRect(prob, 50, 10, 1500, 12, 0.9f);

        List<Quad> quads = pp.process(prob);
        assertTrue("Streifen muss gefiltert werden, war: " + quads.size(), quads.isEmpty());
    }

    @Test
    public void stripeFilter_keepsNormalTextLine() {
        // Normale Textzeile 300×30: aspect=10 < 25 → Filter greift gar nicht.
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.0);
        float[][] prob = makeProb(400, 64);
        fillRect(prob, 50, 16, 300, 30, 0.9f);

        List<Quad> quads = pp.process(prob);
        assertEquals("Normale Textzeile bleibt erhalten", 1, quads.size());
    }

    @Test
    public void stripeFilter_keepsLongLineWithVerticalInkVariance() {
        // Lange, schmale Region mit echter vertikaler Struktur (ein „Hahnenkamm" — Rect mit
        // Glyph-ähnlichen Auf-/Absteigern in einigen Zeilen). Aspect groß, aber Variance hoch.
        DbPostProcessor pp = new DbPostProcessor(0.3, 0.6, 1.0);
        float[][] prob = makeProb(1600, 32);
        // Basisband 1500×6 (kompakt) + dünne Auswüchse oben in nur einer Spalten-Region.
        fillRect(prob, 50, 12, 1500, 6, 0.9f);
        // In den Zeilen 8..11 nur einen Bruchteil der Spalten füllen → variable Zeilenfüllung.
        for (int xx = 60; xx < 200; xx++) {
            for (int yy = 8; yy < 12; yy++) prob[yy][xx] = 0.9f;
        }
        List<Quad> quads = pp.process(prob);
        assertEquals("Region mit Höhenstruktur bleibt erhalten", 1, quads.size());
    }
}
