package nl.quintor.studybits.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.entity.Transcript;

@NoArgsConstructor
@Getter
@Setter
public abstract class Parser {
    private String name;
    private String url;

    public Parser(String name, String url) {
        this.name = name;
        this.url = url;
    }
    public abstract String callDataSource(int id, String url, String endpoint);
    public abstract String parseStudent(int studentid);
    public abstract Transcript parseTranscript(String data);

}
