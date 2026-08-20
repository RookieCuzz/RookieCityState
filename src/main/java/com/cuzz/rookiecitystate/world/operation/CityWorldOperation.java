package com.cuzz.rookiecitystate.world.operation;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public final class CityWorldOperation {
    private final File file;
    private final YamlConfiguration yaml;

    CityWorldOperation(File file, YamlConfiguration yaml) {
        this.file = file;
        this.yaml = yaml;
    }

    public UUID id() { return UUID.fromString(yaml.getString("id")); }
    public UUID cityStateId() { return UUID.fromString(yaml.getString("city_uuid")); }
    public String worldName() { return yaml.getString("world_name"); }
    public WorldOperationKind kind() { return WorldOperationKind.valueOf(yaml.getString("kind")); }
    public String phase() { return yaml.getString("phase", "PREPARED"); }
    public PaymentState paymentState() {
        return PaymentState.valueOf(yaml.getString("payment.state", PaymentState.NOT_CHARGED.name()));
    }
    public long updatedAt() { return yaml.getLong("updated_at"); }
    public String payer() { return yaml.getString("payment.payer"); }
    public String paymentType() { return yaml.getString("payment.type"); }
    public double paymentAmount() { return yaml.getDouble("payment.amount"); }

    public synchronized void phase(String phase) {
        yaml.set("phase", phase);
        yaml.set("updated_at", System.currentTimeMillis());
        save();
    }

    public synchronized void payment(PaymentState state, UUID payer, String type, double amount) {
        yaml.set("payment.state", state.name());
        if (payer != null) yaml.set("payment.payer", payer.toString());
        if (type != null) yaml.set("payment.type", type);
        yaml.set("payment.amount", amount);
        yaml.set("updated_at", System.currentTimeMillis());
        save();
    }

    public synchronized void error(Throwable error) {
        yaml.set("last_error", error == null ? null : error.getClass().getSimpleName() + ": " + error.getMessage());
        yaml.set("updated_at", System.currentTimeMillis());
        save();
    }

    public synchronized void set(String path, Object value) {
        yaml.set(path, value);
        yaml.set("updated_at", System.currentTimeMillis());
        save();
    }

    public synchronized String getString(String path) { return yaml.getString(path); }
    public synchronized long getLong(String path) { return yaml.getLong(path); }
    public synchronized void save() { YamlFiles.save(yaml, file); }
    public synchronized void complete() {
        yaml.set("phase", "COMPLETE");
        yaml.set("completed_at", System.currentTimeMillis());
        save();
    }
    public File file() { return file; }
}
