package com.example.domotika;

import android.os.AsyncTask;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiService {

    // ✅ BASE URL CORREGIDA (sin /usuarios al final)
    private static final String BASE_URL = "https://domotica.bsite.net/api/";

    // Método para registrar usuario
    public void registrarUsuario(String nombre, String email, String password, ApiCallback callback) {
        new RegisterTask(callback).execute(nombre, email, password);
    }

    // Método para iniciar sesión
    public void loginUsuario(String email, String password, ApiCallback callback) {
        new LoginTask(callback).execute(email, password);
    }

    // Task para registro
    private class RegisterTask extends AsyncTask<String, Void, String> {
        private ApiCallback callback;
        private String error;

        public RegisterTask(ApiCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                String nombre = params[0];
                String email = params[1];
                String password = params[2];

                // ✅ URL CORREGIDA para registro
                URL url = new URL(BASE_URL + "usuarios");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Crear JSON
                JSONObject json = new JSONObject();
                json.put("nombre", nombre);
                json.put("email", email);
                json.put("password", password);

                // Log para debug
                android.util.Log.d("API_REGISTRO", "URL: " + url.toString());
                android.util.Log.d("API_REGISTRO", "JSON: " + json.toString());

                // Enviar datos
                OutputStream os = connection.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();
                os.close();

                // Leer respuesta
                int responseCode = connection.getResponseCode();
                android.util.Log.d("API_REGISTRO", "Response Code: " + responseCode);

                BufferedReader reader;

                if (responseCode == 200 || responseCode == 201) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                android.util.Log.d("API_REGISTRO", "Response: " + response.toString());

                if (responseCode != 200 && responseCode != 201) {
                    error = "Error " + responseCode + ": " + response.toString();
                    return null;
                }

                return response.toString();

            } catch (Exception e) {
                android.util.Log.e("API_REGISTRO", "Exception: " + e.getMessage());
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError(error != null ? error : "Error desconocido");
            }
        }
    }

    // Task para login
    private class LoginTask extends AsyncTask<String, Void, String> {
        private ApiCallback callback;
        private String error;

        public LoginTask(ApiCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                String email = params[0];
                String password = params[1];

                // ✅ URL CORREGIDA para login
                URL url = new URL(BASE_URL + "usuarios/login");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Crear JSON
                JSONObject json = new JSONObject();
                json.put("email", email);
                json.put("password", password);

                // Log para debug
                android.util.Log.d("API_LOGIN", "URL: " + url.toString());
                android.util.Log.d("API_LOGIN", "JSON: " + json.toString());

                // Enviar datos
                OutputStream os = connection.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();
                os.close();

                // Leer respuesta
                int responseCode = connection.getResponseCode();
                android.util.Log.d("API_LOGIN", "Response Code: " + responseCode);

                BufferedReader reader;

                if (responseCode == 200) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                android.util.Log.d("API_LOGIN", "Response: " + response.toString());

                if (responseCode != 200) {
                    if (responseCode == 401) {
                        error = "Credenciales incorrectas";
                    } else if (responseCode == 404) {
                        error = "Usuario no encontrado";
                    } else {
                        error = "Error " + responseCode + ": " + response.toString();
                    }
                    return null;
                }

                return response.toString();

            } catch (Exception e) {
                android.util.Log.e("API_LOGIN", "Exception: " + e.getMessage());
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError(error != null ? error : "Error de conexión");
            }
        }
    }
}

interface ApiCallback {
    void onSuccess(String response);
    void onError(String error);
}