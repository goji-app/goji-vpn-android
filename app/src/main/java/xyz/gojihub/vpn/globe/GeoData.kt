package xyz.gojihub.vpn.globe

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Готовые к OpenGL данные из assets/geo_globe.json (сгенерирован tools/gen_globe_geo.mjs). */
class GeoData(
    val coastLines: FloatArray,   // vec3-тройки, по 2 вершины на отрезок — для GL_LINES
    val borderLines: FloatArray,  // то же для государственных границ
    val countryRings: Map<String, List<FloatArray>>, // properties.name -> кольца (vec3 по GL_LINE_LOOP)
    // Те же кольца, но в исходных (lon, lat) — для Triangulator.earClip (заливка страны):
    // плоская 2D-триангуляция по долготе/широте, не по проекции на сферу, см. комментарий там.
    val countryRingsLonLat: Map<String, List<DoubleArray>>
) {
    companion object {
        suspend fun load(context: Context, radius: Float = GlobeMath.RADIUS): GeoData =
            withContext(Dispatchers.IO) {
                val text = context.assets.open("geo_globe.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)

                fun segsToLines(key: String): FloatArray {
                    val arr = root.getJSONArray(key)
                    val out = FloatArray(arr.length() * 6)
                    for (i in 0 until arr.length()) {
                        val seg = arr.getJSONArray(i)
                        val a = GlobeMath.toVec(seg.getDouble(1), seg.getDouble(0), radius)
                        val b = GlobeMath.toVec(seg.getDouble(3), seg.getDouble(2), radius)
                        val o = i * 6
                        out[o] = a[0]; out[o + 1] = a[1]; out[o + 2] = a[2]
                        out[o + 3] = b[0]; out[o + 4] = b[1]; out[o + 5] = b[2]
                    }
                    return out
                }

                val coast = segsToLines("coast")
                val borders = segsToLines("borders")

                val countriesJson = root.getJSONObject("countries")
                val countries = HashMap<String, List<FloatArray>>(countriesJson.length())
                val countriesLonLat = HashMap<String, List<DoubleArray>>(countriesJson.length())
                for (name in countriesJson.keys()) {
                    val ringsArr = countriesJson.getJSONArray(name)
                    val rings = ArrayList<FloatArray>(ringsArr.length())
                    val ringsLonLat = ArrayList<DoubleArray>(ringsArr.length())
                    for (r in 0 until ringsArr.length()) {
                        val ring = ringsArr.getJSONArray(r)
                        val pts = FloatArray(ring.length() * 3)
                        val lonLat = DoubleArray(ring.length() * 2)
                        for (p in 0 until ring.length()) {
                            val pt = ring.getJSONArray(p)
                            val lon = pt.getDouble(0); val lat = pt.getDouble(1)
                            val v = GlobeMath.toVec(lat, lon, radius)
                            pts[p * 3] = v[0]; pts[p * 3 + 1] = v[1]; pts[p * 3 + 2] = v[2]
                            lonLat[p * 2] = lon; lonLat[p * 2 + 1] = lat
                        }
                        rings.add(pts)
                        ringsLonLat.add(lonLat)
                    }
                    countries[name] = rings
                    countriesLonLat[name] = ringsLonLat
                }

                GeoData(coast, borders, countries, countriesLonLat)
            }
    }
}
