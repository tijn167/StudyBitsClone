package nl.quintor.studybits.university.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.quintor.studybits.university.entities.SchemaDefinitionRecord;
import nl.quintor.studybits.university.enums.ExchangePositionState;

import java.util.HashMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExchangePositionModel {
    private String universityName;
    private SchemaDefinitionRecord schemaDefinitionRecord;
    private Long universitySeqNo;
    private ExchangePositionState state;
    private HashMap<String, String> attributes;
}
