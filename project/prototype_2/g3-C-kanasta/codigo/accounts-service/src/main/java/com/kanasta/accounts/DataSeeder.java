package com.kanasta.accounts;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

// Creates the demo accounts the first time the service starts
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;

    public DataSeeder(UserRepository users, BCryptPasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        users.create("Rosa Martínez", "admin@tienda.com", encoder.encode("admin123"),
                "300 555 0101", "Calle 45 # 13-20, Bogotá", "ADMIN");
        users.create("Ana Gómez", "ana@correo.com", encoder.encode("cliente123"),
                "310 555 0202", "Calle 45 # 12-30, Bogotá", "CUSTOMER");
        users.create("Carlos Ruiz", "carlos@correo.com", encoder.encode("cliente123"),
                "320 555 0303", "Carrera 15 # 47-10, Bogotá", "CUSTOMER");
        System.out.println("Demo accounts created");
    }
}
