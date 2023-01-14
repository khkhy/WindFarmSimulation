package com.windenergy.windfarmsimulationv2

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.widget.ProgressBar

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.forEach

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.*
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.OutputStream

import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.URLEncoder
import java.nio.DoubleBuffer.wrap
import kotlin.math.*

class MainActivity : AppCompatActivity(), MapboxMap.OnMapClickListener {
    companion object {
        private val FILL_LAYER_ID = "fill-layer-id"
        private val FILL_SOURCE_ID = "fill-source-id"
        private val ID_ICON_LOCATION = "location"
        private val ID_ICON_LOCATION_SELECTED = "location_selected"
        private val ID_ICON_TURBINE ="turbine"
    }

    private val wt_type: ArrayList<String> = ArrayList<String>()
    private var turbineBool: Boolean = false
    private var count: Long = 0
    private var symbolId: Long = 0
    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var fillSource: GeoJsonSource? = null
    var polyHashList: HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>> = HashMap()
    private var rootSymbolId: String? = null
    private var newPolygon: Boolean = false
    private var polygonDone: Boolean = false
    lateinit var symbolManager: SymbolManager
    private lateinit var symbolTur: Symbol
    private var turbineList: ArrayList<Symbol> = ArrayList()
    private var turbinePointList:ArrayList<Point> = ArrayList()
    private var polygonPointList:ArrayList<LatLng> = ArrayList()
    private var alertDialog: AlertDialog? = null
    private var responseContent =""
    private var processBar: ProgressBar? =null
    private var moveBackLatLng: LatLng = LatLng()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this,getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        val undo_fab = findViewById<FloatingActionButton>(R.id.undo_fab)
        val finish_fab = findViewById<FloatingActionButton>(R.id.finish_fab)
        val changeType_fab = findViewById<FloatingActionButton>(R.id.changeType_fab)
        val sendData_fab = findViewById<FloatingActionButton>(R.id.sendData_fab)
        processBar = findViewById<ProgressBar>(R.id.progress_circular)
        wt_type.add("10MW")
        wt_type.add("15MW")
        wt_type.add("20MW")
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap = mapboxMap

            mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                addMarkerIconStyle(it)
                symbolManager = SymbolManager(mapView!!, mapboxMap!!, mapboxMap?.style!!)
                symbolManager.addClickListener(object : OnSymbolClickListener {
                    override fun onAnnotationClick(t: Symbol?): Boolean {
                        turbineBool = t?.iconImage.equals(ID_ICON_TURBINE)
                        highlightSymbol(t)
                        if (turbineBool) {
                            calculateDistance(t!!.geometry)
//                            println(t!!.geometry)
                        }
//                        println("click click: "+ t?.id.toString());
                        if (polyHashList.keys.first().equals(t?.id.toString())) {
                            val polykey = polyHashList.keys.first()
                            polyHashList.put(
                                polykey, Pair(polyHashList.get(polykey)!!.first, true)
                            )
                        }
                        drawPolygon()

                        if (polygonDone && turbineBool){
                            symbolId = (t?.id ?: println(symbolId.toString())) as Long
                            Toast.makeText(applicationContext,"This is Turbine: "+
                                    t?.textAnchor, Toast.LENGTH_LONG).show()

                        }
                        return true
                    }
                })
                val scaleBarPlugin = ScaleBarPlugin(mapView!!,mapboxMap)

                scaleBarPlugin.create(ScaleBarOptions(this));

                symbolManager.addDragListener(object : OnSymbolDragListener {
                    override fun onAnnotationDragStarted(annotation: Symbol?) {
                        turbineBool = annotation?.iconImage.equals(ID_ICON_TURBINE)
                        if (polygonDone && !turbineBool){
                            annotation?.isDraggable = false
                        }
                        highlightSymbol(annotation)
                        return
                    }

                    override fun onAnnotationDrag(annotation: Symbol?) {
                        if (!polygonDone){
                            val polyKey = polyHashList.filter {
                                it.value.first.filterKeys { it.equals(annotation!!.id.toString()) }.keys.size > 0
                            }.keys.first().toString()
                            polyHashList.get(polyKey)?.first?.put(annotation!!.id.toString(), annotation.latLng)
                            drawPolygon()
                        }
                        return
                    }

                    override fun onAnnotationDragFinished(annotation: Symbol?) {
                        val latLng:LatLng = annotation!!.latLng
                        val isOutside = outSidePolygon(latLng)
                        if (!isOutside && polygonDone){
                            annotation.latLng = moveBackLatLng
                            symbolManager.update(annotation)

                            Toast.makeText(applicationContext,"Can not be moved outside the boundary",Toast.LENGTH_LONG).show()
                            println("Turbine is release")
                        }

                        return
                    }
                })

// Map is set up and the style has loaded. Now can add data
                fillSource = initFillSource(it)
                initFillLayer(it)
                mapboxMap.addOnMapClickListener(this)
            }
        }

        undo_fab.setOnClickListener {
            val key = polyHashList.keys.first()
            val lastKey = polyHashList.get(key)?.first?.keys?.last()

            if (!polygonDone) {
                polyHashList.get(key)?.let {
                    if (it.second) {
                        polyHashList.put(key, it.copy(second = false))
                    } else {
                        it.first.remove(lastKey)
                        symbolManager.annotations.forEach { key, value ->
                            if (key.toString().equals(lastKey)) {
                                symbolManager.delete(value)
                            }
                        }
                    }
                }
            }

            if (polygonDone){
                println(symbolId.toString())
                symbolManager.annotations.remove(symbolId)
                symbolManager.updateSource()
                turbineList.forEach { println("List of turbines: $it")}

                var itemToRemove = symbolTur
                for (s:Symbol in turbineList){
                    if( s.id.equals(symbolId)){
                        itemToRemove = s
                    }
                }
                turbineList.remove(itemToRemove)
            }
            drawPolygon()
        }
        finish_fab.setOnClickListener {
            polygonDone = true
            Toast.makeText(applicationContext!!,"Drawing boundary finished, place Turbines",Toast.LENGTH_LONG).show()
        }
        changeType_fab.setOnClickListener {
            if (polygonDone){
                val sym = symbolManager.annotations.get(symbolId)?.textField
                when(sym){
                    wt_type[0] -> symbolManager.annotations.get(symbolId)?.textField = wt_type[1]
                    wt_type[1] -> symbolManager.annotations.get(symbolId)?.textField = wt_type[2]
                    wt_type[2] -> symbolManager.annotations.get(symbolId)?.textField = wt_type[0]
                }
                symbolManager.updateSource()
            }

        }

        sendData_fab.setOnClickListener {
            sendPost()
            processBar?.isVisible=true
        }

//        finish_btn.setOnClickListener(View.OnClickListener {
//            newPolygon = true
//
//        })

        alertDialog()
    }

    fun highlightSymbol(t:Symbol?){
        if (!polygonDone) {
            val symbols = symbolManager.annotations
            symbols.forEach { k, v -> v.iconImage = ID_ICON_LOCATION }
            val lists = ArrayList<Symbol>(symbols.size())
            symbols.forEach { k, v ->
                lists.add(v)
            }
            symbolManager.update(lists)
            t?.iconImage = ID_ICON_LOCATION_SELECTED
            symbolManager.update(t)
        }
        return
    }

    private fun initFillSource(loadedMapStyle: Style): GeoJsonSource {
        val fillFeatureCollection = FeatureCollection.fromFeatures(arrayOf())
        val fillGeoJsonSource = GeoJsonSource(FILL_SOURCE_ID, fillFeatureCollection)
        loadedMapStyle.addSource(fillGeoJsonSource)
        return fillGeoJsonSource
    }

    private fun initFillLayer(loadedMapStyle: Style) {
        val fillLayer = LineLayer(
            FILL_LAYER_ID,
            FILL_SOURCE_ID
        )
        fillLayer.setProperties(
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineColor(Color.parseColor("#ffffff"))
        )
        loadedMapStyle.addLayerBelow(fillLayer,symbolManager.layerId)
    }

    override fun onMapClick(point: LatLng): Boolean {
        println("point Coordinates: "+ point)
        val symbolOptions: SymbolOptions
        if (!polygonDone){
            polygonPointList.add(point)
            symbolOptions = SymbolOptions()
                .withLatLng(point)
                .withIconImage(ID_ICON_LOCATION)
                .withDraggable(true)
                .withIconSize(1f)
            val symbol: Symbol = symbolManager.create(symbolOptions)

            symbolManager.iconAllowOverlap = true

            if (polyHashList.values.isEmpty()) {
                rootSymbolId = symbol.id.toString()
            } else if (newPolygon) {
                newPolygon = false
                rootSymbolId = symbol.id.toString()
            }

            if (polyHashList.get(rootSymbolId) == null) {
                polyHashList.put(rootSymbolId!!,Pair(linkedMapOf(symbol.id.toString() to point), false))
            } else {
                polyHashList.get(rootSymbolId)?.first?.put(symbol.id.toString(), point)
            }
        }
        if (polygonDone){
            turbineSymbol(point)
        }
        drawPolygon()
        return false
    }
    private fun turbineSymbol(point:LatLng){
        val isOutSide = outSidePolygon(point)
        if (isOutSide) {
            moveBackLatLng = point
            count = count + 1
            val list: Array<Float> = arrayOf(0f, 1.2f)
            val symbolOptionsTurbine: SymbolOptions =
                SymbolOptions().withLatLng(point)
                    .withIconImage(ID_ICON_TURBINE)
                    .withTextAnchor(count.toString())
                    .withTextField(wt_type[0])
                    .withTextOffset(list)
                    .withDraggable(true)
                    .withIconSize(1f)
            symbolOptionsTurbine.data
            symbolTur = symbolManager.create(symbolOptionsTurbine)
            turbineList.add(symbolTur)
            symbolId = symbolTur.id
            symbolManager.iconAllowOverlap = true
            calculateDistance(symbolTur.geometry)
//        turbineList.forEach { println("List of turbines: $it")}
        }else{
            Toast.makeText(applicationContext,"Can not place outside the boundary ", Toast.LENGTH_LONG).show()
        }
    }


    private fun outSidePolygon(latLng: LatLng):Boolean{
        val lat= arrayListOf<Double>()
        val lng= arrayListOf<Double>()
        for (point:LatLng in polygonPointList){
            lat.add(point.latitude)
            lng.add(point.longitude)
        }
        val maxY = lat.maxOrNull() ?: 0.0
        val maxX = lng.maxOrNull() ?: 0.0
        val minY = lat.minOrNull()?:0.0
        val minX = lng.minOrNull()?:0.0

        if (latLng.latitude < minY||latLng.latitude > maxY ||latLng.longitude < minX||
            latLng.longitude > maxX){
            //Not inside polygon
            return false
        }
//        var isInside = false
//        var i = 0
//        var j: Int = polygonPointList.size - 1
//
//            for (pol in  polygonPointList) {
//                if ((pol.latitude > latLng.longitude) != (polygonPointList.get(j).latitude > latLng.latitude) &&
//                    latLng.longitude < (polygonPointList.get(j).longitude - polygonPointList.get(i).longitude) *
//                    (latLng.latitude - polygonPointList.get(i).latitude) / (polygonPointList.get(j).latitude -
//                            polygonPointList.get(i).latitude) + polygonPointList.get(i).longitude) {
//                    isInside = !isInside
//                }
//                j = i++
//            }

        return true
    }

    private fun jsonParser():JSONObject{
        val jsonArrayFlag=JSONArray()
        val jsonObjAll = JSONObject()
        polyHashList.values.forEach { pair ->
            pair.first.values.forEach { latLng ->
                val json=JSONObject()
                json.put("flagLat",latLng.latitude)
                json.put("flagLng",latLng.longitude)
                jsonArrayFlag.put(json)
                jsonObjAll.put("BoundaryFlag",jsonArrayFlag)
            }
        }
        val jsonArrayWT = JSONArray()
        for (s: Symbol in turbineList) {

            val json=JSONObject()
            val wtId = s.textAnchor
            val wtType = s.textField.toString()
            val wtCoordinates =s.latLng
            wtCoordinates.altitude+=70.0 //Hard coded altitude
            json.put("wtId",wtId)
            json.put("wtType", wtType)
            json.put("wtLat", wtCoordinates.latitude)
            json.put("wtLng", wtCoordinates.longitude)
            json.put("wtAltitude", wtCoordinates.altitude)
            jsonArrayWT.put(json)
            jsonObjAll.put("turbinelist", jsonArrayWT)
        }
        return jsonObjAll
    }

    private fun sendPost(){
        val thread = Thread {
            try {
                val url = URL ("http://10.0.2.2:8080")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true
                val jsonParam:JSONObject = jsonParser()
                val outStream =  DataOutputStream(conn.outputStream)
                outStream.writeBytes(jsonParam.toString())
                outStream.flush()
                outStream.close()
                println("STATUS: "+ conn.responseCode.toString())
                println("MSG: "+ conn.responseMessage)
                responseContent = conn.inputStream.bufferedReader().readLine()
                println(responseContent)
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runOnUiThread {
                alertDialog?.setMessage(responseContent)
                processBar?.isVisible=false
                alertDialog?.show()
            }
        }
        thread.start()
    }

    private fun calculateDistance(point: Point){
        if (!turbineList.isEmpty() && !turbineBool) {
            turbinePointList.add(Point.fromLngLat(point.longitude(),point.latitude()))
        }
        if (turbineBool){
            val fromThis =point
            var toThis:Point
            for (symbol:Symbol in turbineList){
                toThis =symbol.geometry
                val theDistance = TurfMeasurement.distance(fromThis,toThis,UNIT_METERS)
                println(""+ theDistance + " Meters")
            }
        }
    }

    private fun drawPolygon() {
        if (!polygonDone) {
            val points = polyHashList.values
            val latLngs: List<Point> = points.map {
                val items = it.first.values.toList().map { it ->
                    Point.fromLngLat(it.longitude, it.latitude)
                }.toMutableList()
                if (it.second) {
                    items.add(items.get(0))
                }
                items
            }.first()

            val finalFeatureList: MutableList<Feature> = ArrayList()
            finalFeatureList.add(Feature.fromGeometry(LineString.fromLngLats(latLngs)))

            if (fillSource != null) {
                fillSource?.setGeoJson(
                    FeatureCollection.fromFeatures(
                        listOf(
                            Feature.fromGeometry(LineString.fromLngLats(latLngs))
                        )
                    )
                )
            }
        }
    }

    private fun alertDialog(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Annual Energy Production")
        builder.setNegativeButton("OK",{ dialogInterface: DialogInterface, i: Int -> })
        alertDialog = builder.create()
    }

    private fun addMarkerIconStyle(style: Style) {

        style.addImage(
            ID_ICON_LOCATION,
            BitmapUtils.getBitmapFromDrawable(
                ContextCompat.getDrawable(this, R.drawable.ic_baseline_flag_24))!!,false)

        style.addImage(ID_ICON_LOCATION_SELECTED, BitmapUtils.getBitmapFromDrawable(
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_outlined_flag_24))!!,false)

        style.addImage(
            ID_ICON_TURBINE,
            BitmapUtils.getBitmapFromDrawable(
                ContextCompat.getDrawable(this, R.drawable.ic_three_blade_propeller))!!,false)
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }
}
