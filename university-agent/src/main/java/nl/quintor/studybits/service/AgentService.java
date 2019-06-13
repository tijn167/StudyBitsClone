package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.Verifier;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.*;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.*;
import static nl.quintor.studybits.messages.StudyBitsMessageTypes.EXCHANGE_POSITIONS;

@Service
@Slf4j
public class AgentService {
    @Autowired
    private TrustAnchor universityTrustAnchor;
    @Autowired
    private Issuer universityIssuer;
    @Autowired
    private Verifier universityVerifier;
    @Autowired
    private StudentService studentService;
    @Autowired
    private CredentialDefinitionService credentialDefinitionService;
    @Autowired
    private ExchangePositionService exchangePositionService;
    @Autowired
    private MessageEnvelopeCodec messageEnvelopeCodec;
    @Autowired
    private ParserFactory parserFactory;

    @Value("${nl.quintor.studybits.university.system}")
    private String universitySystem;

    private HashMap<String, Integer> transcripts = new HashMap<>();

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    public MessageEnvelope processMessage(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        String messageTypeURN = messageEnvelope.getMessageType().getURN();

        if (messageTypeURN.equals(GET_REQUEST.getURN())) {
            MessageEnvelope<String> envelopeType = MessageEnvelope.convertEnvelope(messageEnvelope, GET_REQUEST);
            MessageType requestedMessageType = MessageTypes.forURN(messageEnvelopeCodec.decryptMessage(envelopeType).get());

            if(requestedMessageType.equals(CREDENTIAL_OFFERS)) {
                return getCredentialOffers(messageEnvelope.getDid());
            }
            else if(requestedMessageType.equals(EXCHANGE_POSITIONS)) {
                return exchangePositionService.getAll(messageEnvelope.getDid());
            }
        }
          else if (messageTypeURN.equals(CREDENTIAL_REQUEST.getURN())) {
            return handleCredentialRequest(MessageEnvelope.convertEnvelope(messageEnvelope, CREDENTIAL_REQUEST));
           }
        else if (messageTypeURN.equals(PROOF.getURN())) {
            return handleProof(MessageEnvelope.convertEnvelope(messageEnvelope, PROOF));
        }

        throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getMessageType());
    }

    // Student sets up a connection with university agent
    public MessageEnvelope<ConnectionResponse> login(MessageEnvelope<ConnectionRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException, AccessDeniedException {
        ConnectionRequest connectionRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();

        //Get studentID / current user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String studentId = auth.getName();
        ConnectionResponse connectionResponse = universityTrustAnchor.acceptConnectionRequest(connectionRequest).get();
        studentService.setStudentDid(studentId, connectionRequest.getDid());
        Parser parser = parserFactory.getParser(universitySystem);
        if (parser != null & !studentId.isEmpty()) {
            try {
               Student updatedStudent = parser.parseStudent(Integer.parseInt(studentId));
               studentService.saveStudent(updatedStudent);
            }
            catch(NumberFormatException ex) {
                log.debug("Not a valid student number, error: " + ex.getMessage());
            }
        }
        return messageEnvelopeCodec.encryptMessage(connectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, connectionRequest.getDid()).get();
    }

    /**
     * This functions gets all available credential offers for the student and sends them to the  Android application
     * @param did the digital identity of the student wallet
     * @return the message to send
     * @throws JsonProcessingException
     * @throws IndyException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public MessageEnvelope<CredentialOfferList> getCredentialOffers(String did) throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        log.debug("Getting credential offers for did {}", did);
        CredentialOfferList credentialOffers = new CredentialOfferList();
        Student student = studentService.getStudentByStudentDid(did);
        log.debug("Found student for which to get credential offers {}", student);
        for(int i = 0; i < student.getTranscriptList().size(); i++){
        if (student.getTranscriptList().get(i) != null && !student.getTranscriptList().get(i).isProven()) {
            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), did).get();
            credentialOffers.addCredentialOffer(credentialOffer);
            log.debug("Returning credentialOffers {}", credentialOffers);
            transcripts.put(credentialOffer.getNonce(), i);
            }
        }

        return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS, did).get();
//        return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS, did).get();
    }


    private MessageEnvelope handleCredentialRequest(MessageEnvelope<CredentialRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, UnsupportedEncodingException, JsonProcessingException {
        CredentialRequest credentialRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();
        log.debug("Decrypted request");
        int position = transcripts.get(credentialRequest.getCredentialOffer().getNonce());
        Student student = studentService.getStudentByStudentDid(messageEnvelope.getDid());
        Map<String, Object> values = new HashMap<>();
        values.put("full_name", student.getFirstName() + " " + student.getLastName());
        values.put("type", student.getTranscriptList().get(position).getTranscriptType());
        values.put("degree", student.getTranscriptList().get(position).getTranscriptName());
        values.put("courses", student.getTranscriptList().get(position).coursesToString());
        values.put("totalec", student.getTranscriptList().get(position).getTotalEC());
        values.put("completiondate", student.getTranscriptList().get(position).getReceivedDate());
        CredentialWithRequest credentialWithRequest = universityIssuer.createCredential(credentialRequest, values).get();

        studentService.proveTranscript(student.getStudentId(), position);

        return messageEnvelopeCodec.encryptMessage(credentialWithRequest, IndyMessageTypes.CREDENTIAL, messageEnvelope.getDid()).get();
    }

    private MessageEnvelope handleProof(MessageEnvelope<Proof> proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        log.debug("Handling proof");
        Student student = studentService.getStudentByStudentDid(proofEnvelope.getDid());
        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);

        Proof proof = messageEnvelopeCodec.decryptMessage(proofEnvelope).get();
        log.debug("Proof: {}", proof);
        List<ProofAttribute> proofAttributes = universityVerifier.getVerifiedProofAttributes(proofRequest, proof, proofEnvelope.getDid()).get();


        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return null;
    }
}
