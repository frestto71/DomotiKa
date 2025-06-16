package com.example.domotika;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmailLogin, etPasswordLogin;
    private Button btnLogin;
    private ProgressBar progressBarLogin;
    private TextView tvRegistro, tvForgotPassword;
    private ApiService apiService;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Verificar si ya está logueado
        sharedPreferences = getSharedPreferences("DomotikaPrefs", MODE_PRIVATE);
        if (isUserLoggedIn()) {
            goToMainActivity();
            return;
        }

        initViews();
        setupClickListeners();

        // Inicializar servicio API
        apiService = new ApiService();
    }

    private void initViews() {
        etEmailLogin = findViewById(R.id.etEmailLogin);
        etPasswordLogin = findViewById(R.id.etPasswordLogin);
        btnLogin = findViewById(R.id.btnLogin);
        progressBarLogin = findViewById(R.id.progressBarLogin);
        tvRegistro = findViewById(R.id.tvRegistro);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> iniciarSesion());

        tvRegistro.setOnClickListener(v -> {
            // Ir a pantalla de registro
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            // Implementar recuperación de contraseña
            Toast.makeText(this, "Funcionalidad próximamente", Toast.LENGTH_SHORT).show();
        });
    }

    private void iniciarSesion() {
        String email = etEmailLogin.getText().toString().trim();
        String password = etPasswordLogin.getText().toString().trim();

        // Validaciones
        if (email.isEmpty()) {
            etEmailLogin.setError("Ingresa tu email");
            etEmailLogin.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmailLogin.setError("Email inválido");
            etEmailLogin.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPasswordLogin.setError("Ingresa tu contraseña");
            etPasswordLogin.requestFocus();
            return;
        }

        // Mostrar loading
        mostrarLoading(true);

        // Llamar API de login
        apiService.loginUsuario(email, password, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    mostrarLoading(false);

                    try {
                        // Procesar respuesta exitosa
                        // Guardar datos de usuario
                        guardarSesionUsuario(email, response);

                        Toast.makeText(LoginActivity.this, "Bienvenido", Toast.LENGTH_SHORT).show();
                        goToMainActivity();

                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Error al procesar respuesta", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    mostrarLoading(false);

                    // Mostrar error específico
                    if (error.contains("401") || error.contains("Unauthorized")) {
                        Toast.makeText(LoginActivity.this, "Email o contraseña incorrectos", Toast.LENGTH_LONG).show();
                    } else if (error.contains("404") || error.contains("No encontrado")) {
                        Toast.makeText(LoginActivity.this, "Usuario no encontrado", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void mostrarLoading(boolean mostrar) {
        if (mostrar) {
            progressBarLogin.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
            btnLogin.setText("Iniciando...");
        } else {
            progressBarLogin.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            btnLogin.setText("Iniciar Sesión");
        }
    }

    private void guardarSesionUsuario(String email, String userInfo) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("user_email", email);
        editor.putString("user_info", userInfo);
        editor.putBoolean("is_logged_in", true);
        editor.putLong("login_time", System.currentTimeMillis());
        editor.apply();
    }

    private boolean isUserLoggedIn() {
        return sharedPreferences.getBoolean("is_logged_in", false);
    }

    private void goToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}