package mb.oauth2authorizationserver.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RestPageImpl<T> extends PageImpl<T> {

    @JsonCreator
    public RestPageImpl(@JsonProperty("content") List<T> content,
                        @JsonProperty("number") Integer number,
                        @JsonProperty("size") Integer size,
                        @JsonProperty("totalElements") Long totalElements,
                        @JsonProperty("page") PageInfo page) {
        super(
                content,
                PageRequest.of(
                        page != null ? page.number() : (number != null ? number : 0),
                        page != null ? page.size() : (size != null ? size : content.size())
                ),
                page != null ? page.totalElements() : (totalElements != null ? totalElements : content.size())
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(@JsonProperty("number") int number,
                           @JsonProperty("size") int size,
                           @JsonProperty("totalElements") long totalElements,
                           @JsonProperty("totalPages") int totalPages) {
    }
}
