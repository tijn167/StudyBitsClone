package nl.quintor.studybits;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.hyperledger.indy.sdk.ledger.LedgerInvalidTransactionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.hyperledger.indy.sdk.pool.Pool;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_RESPONSE;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.VERINYM;

@Component
@Profile("mobile-test")
@Slf4j
public class LedgerSeeder {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    private boolean done = false;

    @EventListener
    public void seed(ContextRefreshedEvent event) throws InterruptedException, ExecutionException, IndyException, IOException {
        if (needsSeeding()) {
            Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
            String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
            IndyPool indyPool = new IndyPool(poolName);
            IndyWallet stewardWallet = IndyWallet.create(indyPool, "steward" + System.currentTimeMillis(), "000000000000000000000000Steward1");
            TrustAnchor steward = new TrustAnchor(stewardWallet);

            Issuer university = new Issuer(IndyWallet.create(indyPool, "university" + System.currentTimeMillis(),
                    StringUtils.leftPad(universityName.replace(" ", ""), 32, '0')));


            onboardIssuer(steward, university);



            Issuer stewardIssuer = new Issuer(stewardWallet);
            if (universityName.equals("Rijksuniversiteit Groningen")) {
                String schemaId = stewardIssuer.createAndSendSchema("Transcript", "1.0", "first_name", "last_name", "degree", "status", "average").get();

                credentialDefinitionService.createCredentialDefintion(schemaId);

                RestTemplate restTemplate = new RestTemplate();

                ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8081/bootstrap/credential_definition/" + schemaId, null, String.class);
                response = restTemplate.postForEntity("http://localhost:8081/bootstrap/exchange_position/" + credentialDefinitionService.getCredentialDefinitionId(), null, String.class);
            }




            done = true;
            log.info("Finished seeding ledger");
        }
    }

    public static void onboardIssuer(TrustAnchor steward, Issuer newcomer) throws InterruptedException, ExecutionException, IndyException, IOException {
        // Connecting newcomer with Steward

        // #Step 4.1.2 & 4.1.3
        // Create new DID (For steward_faber connection) and send NYM request to ledger
        String governmentConnectionRequest = MessageEnvelope.encryptMessage(steward.createConnectionRequest(newcomer.getName(), "TRUST_ANCHOR").get(),
                IndyMessageTypes.CONNECTION_REQUEST, null).get().toJSON();

        // #Step 4.1.4 & 4.1.5
        // Steward sends connection request to Faber
        ConnectionRequest connectionRequest = MessageEnvelope.parseFromString(governmentConnectionRequest, CONNECTION_REQUEST).extractMessage(newcomer).get();

        // #Step 4.1.6
        // Faber accepts the connection request from Steward
        ConnectionResponse newcomerConnectionResponse = newcomer.acceptConnectionRequest(connectionRequest).get();

        // #Step 4.1.9
        // Faber creates a connection response with its created DID and Nonce from the received request from Steward
        String newcomerConnectionResponseString =  MessageEnvelope.encryptMessage(newcomerConnectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, newcomer).get().toJSON();

        // #Step 4.1.13
        // Steward decrypts the anonymously encrypted message from Faber
        ConnectionResponse connectionResponse = MessageEnvelope.parseFromString(newcomerConnectionResponseString, CONNECTION_RESPONSE).extractMessage(steward).get();

        // #Step 4.1.14 & 4.1.15
        // Steward authenticates Faber
        // Steward sends the NYM Transaction for Faber's DID to the ledger
        steward.acceptConnectionResponse(connectionResponse).get();

        // #Step 4.2.1 t/m 4.2.4
        // Faber needs a new DID to interact with identiy owners, thus create a new DID request steward to write on ledger
        String verinymRequest = MessageEnvelope.encryptMessage(newcomer.createVerinymRequest(MessageEnvelope.parseFromString(governmentConnectionRequest, CONNECTION_REQUEST).extractMessage(newcomer).get()
                .getDid()), IndyMessageTypes.VERINYM, newcomer).get().toJSON();

        // #step 4.2.5 t/m 4.2.8
        // Steward accepts verinym request from Faber and thus writes the new DID on the ledger
        steward.acceptVerinymRequest(MessageEnvelope.parseFromString(verinymRequest, VERINYM).extractMessage(steward).get()).get();
    }

    public boolean needsSeeding() {
        return !done;
    }
}
