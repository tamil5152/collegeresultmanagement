package com.college.result.config;

import com.college.result.common.Patterns;
import com.college.result.model.Role;
import com.college.result.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on an empty production database from environment variables
 * (BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD). Does nothing if an admin already exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AppProperties props, UserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin cfg = props.bootstrapAdmin();
        if (cfg == null || isBlank(cfg.username()) || isBlank(cfg.password())) {
            return;
        }
        if (users.existsAdmin()) {
            log.info("Bootstrap admin skipped: an administrator already exists");
            return;
        }
        if (!cfg.username().matches(Patterns.USERNAME) || !cfg.password().matches(Patterns.PASSWORD)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD do not meet the "
                    + "username/password rules (password: 8+ characters with a letter and a digit)");
        }
        users.insert(cfg.username(), isBlank(cfg.email()) ? "admin@example.com" : cfg.email(),
                encoder.encode(cfg.password()), isBlank(cfg.fullName()) ? "System Administrator" : cfg.fullName(),
                Role.ADMIN);
        log.info("Bootstrap administrator '{}' created. Remove BOOTSTRAP_ADMIN_PASSWORD from the environment.",
                cfg.username());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
