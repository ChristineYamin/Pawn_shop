package com.example.pawn_ledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class OwnerPinStore {

    private final SharedPreferences preferences;

    public OwnerPinStore(Context context) {
        preferences = context.getSharedPreferences(
                "owner_pin",
                Context.MODE_PRIVATE
        );
    }

    public boolean hasPin() {
        return preferences.contains("pin_hash");
    }

    public boolean setPin(String pin) {
        if (hasPin()) {
            return false;
        }

        if (!pin.matches("[0-9]{6}")) {
            return false;
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        byte[] hash = hashPin(pin, salt);

        return preferences.edit()
                .putString(
                        "pin_salt",
                        Base64.encodeToString(salt, Base64.NO_WRAP)
                )
                .putString(
                        "pin_hash",
                        Base64.encodeToString(hash, Base64.NO_WRAP)
                )
                .commit();
    }

    public boolean verifyPin(String pin) {
        if (!hasPin() || !pin.matches("[0-9]{6}")) {
            return false;
        }

        String savedSalt = preferences.getString("pin_salt", null);
        String savedHash = preferences.getString("pin_hash", null);

        if (savedSalt == null || savedHash == null) {
            return false;
        }

        byte[] salt = Base64.decode(savedSalt, Base64.NO_WRAP);
        byte[] expectedHash = Base64.decode(savedHash, Base64.NO_WRAP);
        byte[] enteredHash = hashPin(pin, salt);

        return MessageDigest.isEqual(expectedHash, enteredHash);
    }

    private byte[] hashPin(String pin, byte[] salt) {
        PBEKeySpec specification = new PBEKeySpec(
                pin.toCharArray(),
                salt,
                120_000,
                256
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA1"
                    );

            return factory.generateSecret(specification).getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Could not process the PIN",
                    e
            );
        } finally {
            specification.clearPassword();
        }
    }
}