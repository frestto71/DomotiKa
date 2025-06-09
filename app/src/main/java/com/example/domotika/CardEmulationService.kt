package com.example.domotika.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class CardEmulationService : HostApduService() {

    private val AID = "F1234567890ABCDE" // AID de la tarjeta emulada

    // Este método procesará los comandos APDU enviados por el lector NFC
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        // Lógica para procesar los comandos APDU del lector NFC
        // En este caso, siempre responde con un ID predefinido (emulando una tarjeta)
        val response = "Emulación de tarjeta ID: $AID".toByteArray()
        return response
    }

    // Método para manejar cuando se desactiva la emulación
    override fun onDeactivated(reason: Int) {
        // Lógica cuando se desactiva el servicio HCE
    }
}
