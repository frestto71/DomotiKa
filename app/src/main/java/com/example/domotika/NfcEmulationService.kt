import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class NfcEmulationService : HostApduService() {

    // AID para la emulación
    private val SELECT_APDU = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())
    private val SELECT_RESPONSE = byteArrayOf(0x90.toByte(), 0x00.toByte()) // Respuesta de éxito

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        return if (isSelectApdu(commandApdu)) {
            SELECT_RESPONSE  // Responde con éxito si el comando es SELECT
        } else {
            byteArrayOf()  // Respuesta vacía si el comando no es reconocido
        }
    }

    override fun onDeactivated(reason: Int) {
        // Lógica cuando la emulación es desactivada
    }

    private fun isSelectApdu(commandApdu: ByteArray): Boolean {
        return commandApdu.contentEquals(SELECT_APDU)  // Verifica si es un comando SELECT
    }
}
