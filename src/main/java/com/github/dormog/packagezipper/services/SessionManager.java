package com.github.dormog.packagezipper.services;

import com.github.dormog.packagezipper.maven.resolver.AbstractEventsCrawlerRepositoryListener;
import com.github.dormog.packagezipper.maven.resolver.Booter;
import com.github.dormog.packagezipper.maven.resolver.ConsoleRepositoryListener;
import com.github.dormog.packagezipper.maven.resolver.EventsCrawlerRepositoryListener;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Data
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SessionManager {
    private final Environment env;
    private DefaultRepositorySystemSession session;
    private RepositorySystem system;
    private AbstractEventsCrawlerRepositoryListener eventsCrawlerRepositoryListener;
    private Booter booter;

    @PostConstruct
    public void initialize() {
        this.eventsCrawlerRepositoryListener = getAbstractEventsCrawlerRepositoryListener();
        this.booter = new Booter(eventsCrawlerRepositoryListener);
        this.system = Booter.newRepositorySystem();
        this.session = booter.newRepositorySystemSession(system);
    }

    private AbstractEventsCrawlerRepositoryListener getAbstractEventsCrawlerRepositoryListener() {
        if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
            return new ConsoleRepositoryListener();
        } else {
            return new EventsCrawlerRepositoryListener();
        }
    }

}
