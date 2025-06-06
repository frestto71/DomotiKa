package com.example.domotika.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * Servicio que responde a APDU del lector NFC.
 * Deberás registrar aquí tu propio AID y, al recibir SELECT, devolver el NDEF con el ID.
 */
class CardEmulationService : HostApduService() {

    companion object {
        private const val TAG = "CardEmulationService"
        // EJEMPLO de AID: debes elegir uno que no choque con otros servicios.
        // Formato: 16 bytes en hexadecimal, sin espacios. (Ejemplo: D2760000850101)
        // Puedes usar un AID corto mientras pruebas.
        const val SAMPLE_AID = "F222222222"  // Único para tu “tarjeta”
        // Comando SELECT en APDU: CLA=0x00, INS=0xA4, P1=0x04, P2=0x00
        val SELECT_APDU_HEADER = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())
        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FAILED = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }

    // Aquí guardaremos dinámicamente el NDEF a enviar. La Activity deberá fijar este valor.
    private var ndefPayload: ByteArray? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        // No es estrictamente necesario hacer nada aquí.
        return super.onStartCommand(intent, flags, startId)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILED

        // 1) Detectar comando SELECT AID
        if (isSelectAidApdu(commandApdu)) {
            // Construir respuesta:[nombre AID o contenido, luego 0x90 0x00]
            // Por simplicidad, aquí devolvemos el NDEF en “raw” + status
            ndefPayload?.let { payload ->
                val response = ByteArray(payload.size + 2)
                System.arraycopy(payload, 0, response, 0, payload.size)
                System.arraycopy(STATUS_SUCCESS, 0, response, payload.size, 2)
                Log.i(TAG, "Enviando NDEF + 0x9000")
                return response
            }
            // Si no hay payload asignado, devolvemos error
            return STATUS_FAILED
        }

        // Si llega otro tipo de APDU (por ejemplo, leer bloques, etc.), puedes responder con STATUS_FAILED
        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "Desactivado: razón=$reason")
        // Quizá limpiar ndefPayload si lo deseas
    }

    /**
     * Verifica si el APDU es un SELECT (0x00 A4 04 00) concatenado al AID que definimos.
     */
    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_APDU_HEADER.size) return false
        for (i in SELECT_APDU_HEADER.indices) {
            if (apdu[i] != SELECT_APDU_HEADER[i]) return false
        }
        // Obtener LC (longitud del AID)
        val lc = apdu[4].toInt() and 0xFF
        // AID en posición 5..(5+lc-1)
        val aidBytes = apdu.copyOfRange(5, 5 + lc)
        val aidString = aidBytes.joinToString(separator = "") { byte ->
            String.format("%02X", byte)
        }
        return aidString.equals(SAMPLE_AID, ignoreCase = true)
    }

    /**
     * Método público para que la Activity fije el NDEF que se va a emular.
     */
    fun setNdefPayload(payload: ByteArray) {
        ndefPayload = payload
    }
}
