package xyz.gojihub.vpn.globe

/**
 * Простая "ear clipping" триангуляция замкнутого кольца в 2D (используется для заливки страны
 * на глобусе — координаты кольца берутся в (lon, lat), не в проекции на сферу: для типичного
 * размера страны на глобусе плоская триангуляция по долготе/широте визуально неотличима от
 * честной сферической, а сам алгоритм на порядок проще и надёжнее сферического варианта).
 *
 * Кольца стран в geo_globe.json — простые многоугольники (без самопересечений), но бывают
 * вогнутыми; классический fan-триангулятор (из одной вершины) на вогнутых кольцах даёт
 * "вылезающие" треугольники за пределы фигуры. Ear clipping корректно работает и на вогнутых
 * контурах. Отдельные "дыры" (например Лесото внутри ЮАР) не различаются от островов — каждое
 * кольцо триангулируется независимо, дыры в редких случаях заливаются тоже; это сознательный
 * компромисс ради простоты (страны с настоящими дырами единичны, а на масштабе глобуса
 * артефакт незаметен).
 */
object Triangulator {

    /** [coords] — плоский массив [x0,y0,x1,y1,...]. Возвращает индексы (в исходном [coords],
     *  т.е. значение i соответствует точке i) тройками — по 3 индекса на треугольник. */
    fun earClip(coords: DoubleArray): IntArray {
        val n0 = coords.size / 2
        if (n0 < 3) return IntArray(0)

        // Убираем дубли последовательных точек (типично — кольцо замкнуто повторением первой
        // точки последней) — иначе такие точки дают вырожденные (нулевой площади) треугольники.
        val idx = ArrayList<Int>(n0)
        for (i in 0 until n0) {
            if (idx.isEmpty()) { idx.add(i); continue }
            val pj = idx[idx.size - 1]
            val dx = coords[i * 2] - coords[pj * 2]
            val dy = coords[i * 2 + 1] - coords[pj * 2 + 1]
            if (dx * dx + dy * dy > 1e-14) idx.add(i)
        }
        if (idx.size > 1) {
            val first = idx[0]; val last = idx[idx.size - 1]
            val dx = coords[first * 2] - coords[last * 2]
            val dy = coords[first * 2 + 1] - coords[last * 2 + 1]
            if (dx * dx + dy * dy < 1e-14) idx.removeAt(idx.size - 1)
        }
        if (idx.size < 3) return IntArray(0)

        fun cross(o: Int, a: Int, b: Int): Double {
            val ox = coords[o * 2]; val oy = coords[o * 2 + 1]
            val ax = coords[a * 2] - ox; val ay = coords[a * 2 + 1] - oy
            val bx = coords[b * 2] - ox; val by = coords[b * 2 + 1] - oy
            return ax * by - ay * bx
        }

        // Знаковая площадь определяет обход (CW/CCW) — приводим к CCW, чтобы тест на
        // выпуклость вершины (cross > 0) был единообразным.
        var signedArea = 0.0
        for (k in idx.indices) {
            val i = idx[k]; val j = idx[(k + 1) % idx.size]
            signedArea += coords[i * 2] * coords[j * 2 + 1] - coords[j * 2] * coords[i * 2 + 1]
        }
        val remaining = ArrayList(idx)
        if (signedArea < 0) remaining.reverse()

        fun pointInTri(p: Int, a: Int, b: Int, c: Int): Boolean {
            val d1 = cross(a, b, p); val d2 = cross(b, c, p); val d3 = cross(c, a, p)
            val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
            val hasPos = d1 > 0 || d2 > 0 || d3 > 0
            return !(hasNeg && hasPos)
        }

        val triangles = ArrayList<Int>((remaining.size - 2) * 3)
        val maxGuard = remaining.size * remaining.size + 16
        var guard = 0
        while (remaining.size > 3 && guard < maxGuard) {
            guard++
            var earFound = false
            for (k in remaining.indices) {
                val iPrev = remaining[(k - 1 + remaining.size) % remaining.size]
                val iCurr = remaining[k]
                val iNext = remaining[(k + 1) % remaining.size]
                if (cross(iPrev, iCurr, iNext) <= 1e-12) continue // не выпуклая — не "ухо"
                var isEar = true
                for (m in remaining.indices) {
                    val iv = remaining[m]
                    if (iv == iPrev || iv == iCurr || iv == iNext) continue
                    if (pointInTri(iv, iPrev, iCurr, iNext)) { isEar = false; break }
                }
                if (isEar) {
                    triangles.add(iPrev); triangles.add(iCurr); triangles.add(iNext)
                    remaining.removeAt(k)
                    earFound = true
                    break
                }
            }
            // Численный тупик (не нашли ни одного "уха") — выходим из цикла и достраиваем
            // оставшееся веером ниже, вместо зависания.
            if (!earFound) break
        }
        when {
            remaining.size == 3 -> { triangles.add(remaining[0]); triangles.add(remaining[1]); triangles.add(remaining[2]) }
            remaining.size > 3 -> for (k in 1 until remaining.size - 1) {
                triangles.add(remaining[0]); triangles.add(remaining[k]); triangles.add(remaining[k + 1])
            }
        }
        return triangles.toIntArray()
    }
}
