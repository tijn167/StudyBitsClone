package nl.quintor.studybits.config;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.pool.Pool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.File;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class IndyConfiguration {
    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;


    @Bean
    public TrustAnchor universityTrustAnchor(IndyWallet universityWallet) throws Exception {
        return new TrustAnchor(universityWallet);
    }

    @Bean
    public Verifier universityVerifier(IndyWallet universityWallet) {
        return new Verifier(universityWallet);
    }

    @Bean
    public Issuer universityIssuer(IndyWallet universityWallet) {
        return new Issuer(universityWallet);
    }

    @Bean
    public MessageEnvelopeCodec universityCodec(IndyWallet universityWallet) {
        return new MessageEnvelopeCodec(universityWallet);
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public IndyWallet universityWallet() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        log.debug("Initializing wallet using name {}", universityName);

        String name = universityName.replace(" ", "");
        String seed = StringUtils.leftPad(name, 32, '0');
        String poolName = PoolUtils.createPoolLedgerConfig(null);
        IndyPool indyPool = new IndyPool(poolName);
        return IndyWallet.create(indyPool, name, seed);
    }
}
