package ec.gob.cnt.diagnosticoexpress

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 1001
        private const val APN_CNT = "internet.cnt.net.ec"
        private const val NUMERO_WHATSAPP_SAC = "593999999999"
    }

    private lateinit var tvOperador: TextView
    private lateinit var tvEstadoSim: TextView
    private lateinit var tvTecnologiaRed: TextView
    private lateinit var tvDatosMoviles: TextView
    private lateinit var tvCalidadSenal: TextView
    private lateinit var tvConexionActiva: TextView
    private lateinit var tvDiagnosticoApn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOperador = findViewById(R.id.tvOperador)
        tvEstadoSim = findViewById(R.id.tvEstadoSim)
        tvTecnologiaRed = findViewById(R.id.tvTecnologiaRed)
        tvDatosMoviles = findViewById(R.id.tvDatosMoviles)
        tvCalidadSenal = findViewById(R.id.tvCalidadSenal)
        tvConexionActiva = findViewById(R.id.tvConexionActiva)
        tvDiagnosticoApn = findViewById(R.id.tvDiagnosticoApn)

        findViewById<Button>(R.id.btnActualizar).setOnClickListener {
            verificarPermisosYDiagnostico()
            Toast.makeText(this, "Diagnóstico actualizado", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnAbrirApn).setOnClickListener {
            copiarApnAlPortapapeles()
            abrirAjustesSeguros(
                Settings.ACTION_APN_SETTINGS,
                Settings.ACTION_DATA_ROAMING_SETTINGS
            )
        }

        findViewById<Button>(R.id.btnModoRed).setOnClickListener {
            copiarApnAlPortapapeles()
            abrirAjustesSeguros(
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                Settings.ACTION_DATA_ROAMING_SETTINGS
            )
        }

        findViewById<Button>(R.id.btnEnviarWhatsapp).setOnClickListener {
            enviarReporteWhatsApp()
        }

        verificarPermisosYDiagnostico()
    }

    override fun onResume() {
        super.onResume()
        if (tienePermisosNecesarios()) {
            ejecutarDiagnostico()
        }
    }

    private fun tienePermisosNecesarios(): Boolean {
        val permisoTelefono = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val permisoUbicacion = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return permisoTelefono && permisoUbicacion
    }

    private fun verificarPermisosYDiagnostico() {
        if (tienePermisosNecesarios()) {
            ejecutarDiagnostico()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_PERMISSIONS_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            ejecutarDiagnostico()
        }
    }

    private fun ejecutarDiagnostico() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val nombreOperador = telephonyManager.networkOperatorName.orEmpty().ifBlank { "Desconocido" }
        val codigoOperador = telephonyManager.simOperator.orEmpty().ifBlank { telephonyManager.networkOperator.orEmpty() }
        val esCnt = codigoOperador == "74002" || nombreOperador.contains("CNT", ignoreCase = true)
        tvOperador.text = if (codigoOperador.isNotBlank()) {
            "$nombreOperador ($codigoOperador)"
        } else {
            nombreOperador
        }

        val simLista = telephonyManager.simState == TelephonyManager.SIM_STATE_READY
        tvEstadoSim.text = when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> "Activa / Lista"
            TelephonyManager.SIM_STATE_ABSENT -> "Sin tarjeta SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Bloqueada (Requiere PIN)"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Bloqueada (Requiere PUK)"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueada por red"
            else -> "No disponible"
        }

        tvTecnologiaRed.text = obtenerTecnologiaRed(telephonyManager)

        val datosActivos = obtenerEstadoDatosMoviles(telephonyManager)
        tvDatosMoviles.text = if (datosActivos) "Activados" else "Desactivados"

        tvCalidadSenal.text = obtenerCalidadSenal(telephonyManager)

        var wifiActivo = false
        var celularActivo = false
        var internetCelularValidado = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val redActiva = connectivityManager.activeNetwork
            val capacidades = connectivityManager.getNetworkCapabilities(redActiva)
            if (capacidades != null) {
                wifiActivo = capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                celularActivo = capacidades.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                internetCelularValidado = celularActivo &&
                    capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        tvConexionActiva.text = when {
            wifiActivo -> "Wi-Fi"
            celularActivo -> "Datos Móviles"
            else -> "Sin conexión a Internet"
        }

        tvDiagnosticoApn.text = when {
            !simLista -> "⚠️ No se detecta una SIM activa. Verifica la bandeja SIM."
            !esCnt -> "⚠️ La SIM detectada ($codigoOperador) no pertenece a CNT EP (74002)."
            !datosActivos -> "⚠️ Los datos móviles están apagados. Enciéndelos para probar el APN."
            wifiActivo -> "ℹ️ Apaga el Wi-Fi y presiona 'Actualizar Diagnóstico' para validar el APN de CNT."
            internetCelularValidado -> "✅ APN operativo. Sesión de datos activa correctamente en $APN_CNT."
            else -> "🚨 Requiere revisión de APN: Datos encendidos sin navegación. Configura '$APN_CNT'."
        }
    }

    private fun obtenerTecnologiaRed(tm: TelephonyManager): String {
        return try {
            val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            when (tipo) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                else -> "Sin red móvil / No detectada"
            }
        } catch (e: SecurityException) {
            "Permiso requerido"
        }
    }

    private fun obtenerEstadoDatosMoviles(tm: TelephonyManager): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.isDataEnabled
            } else {
                Settings.Global.getInt(contentResolver, "mobile_data", 0) == 1
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun obtenerCalidadSenal(tm: TelephonyManager): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val nivel = tm.signalStrength?.level ?: -1
                when (nivel) {
                    4 -> "Excelente (4/4)"
                    3 -> "Buena (3/4)"
                    2 -> "Moderada (2/4)"
                    1 -> "Baja (1/4)"
                    0 -> "Sin señal (0/4)"
                    else -> "No disponible"
                }
            } else {
                "No disponible en esta versión"
            }
        } catch (e: SecurityException) {
            "Permiso requerido"
        }
    }

    private fun copiarApnAlPortapapeles() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("APN CNT", APN_CNT)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            this,
            "APN copiado al portapapeles: $APN_CNT",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun abrirAjustesSeguros(accionPrincipal: String, accionRespaldo: String) {
        try {
            startActivity(Intent(accionPrincipal))
        } catch (e: Exception) {
            try {
                startActivity(Intent(accionRespaldo))
            } catch (ex: Exception) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
    }

    private fun enviarReporteWhatsApp() {
        val reporte = """
            *REPORTE DIAGNÓSTICO EXPRESS - CNT EP*
            • *Operador:* ${tvOperador.text}
            • *Estado SIM:* ${tvEstadoSim.text}
            • *Tecnología:* ${tvTecnologiaRed.text}
            • *Datos Móviles:* ${tvDatosMoviles.text}
            • *Señal:* ${tvCalidadSenal.text}
            • *Conexión Activa:* ${tvConexionActiva.text}
            • *Diagnóstico APN:* ${tvDiagnosticoApn.text}
        """.trimIndent()

        val url = "https://wa.me/$NUMERO_WHATSAPP_SAC?text=${Uri.encode(reporte)}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}
