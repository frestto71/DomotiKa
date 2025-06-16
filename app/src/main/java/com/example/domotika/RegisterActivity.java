package com.example.domotika;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etNombre, etEmail, etPassword;
    private Button btnRegistrar;
    private ProgressBar progressBar;
    private TextView tvLogin;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupClickListeners();

        // Inicializar servicio API
        apiService = new ApiService();
    }

    private void initViews() {
        etNombre = findViewById(R.id.etNombre);  // ← AGREGAR ESTA LÍNEA
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegistrar = findViewById(R.id.btnRegistrar);
        progressBar = findViewById(R.id.progressBar);
        tvLogin = findViewById(R.id.tvLogin);
    }

    private void setupClickListeners() {
        btnRegistrar.setOnClickListener(v -> registrarUsuario());

        tvLogin.setOnClickListener(v -> {
            // Ir a pantalla de login
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void registrarUsuario() {
        String nombre = etNombre.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validaciones
        if (nombre.isEmpty()) {
            etNombre.setError("Ingresa tu nombre");
            etNombre.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            etEmail.setError("Ingresa tu email");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email inválido");
            etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Ingresa una contraseña");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Contraseña muy corta (mínimo 6 caracteres)");
            etPassword.requestFocus();
            return;
        }

        // Mostrar loading
        mostrarLoading(true);

        // Llamar API
        apiService.registrarUsuario(nombre, email, password, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    mostrarLoading(false);
                    Toast.makeText(RegisterActivity.this, "Registro exitoso", Toast.LENGTH_SHORT).show();

                    // Ir a pantalla principal o login
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    mostrarLoading(false);
                    Toast.makeText(RegisterActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void mostrarLoading(boolean mostrar) {
        if (mostrar) {
            progressBar.setVisibility(View.VISIBLE);
            btnRegistrar.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRegistrar.setEnabled(true);
        }
    }
}