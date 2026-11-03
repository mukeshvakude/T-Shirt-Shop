package com.shop.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.shop.exception.BadRequestException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DesignCanvasValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MINE = "/media/designs/11111111-2222-3333-4444-555555555555.png";
    private static final Set<String> OWN = Set.of(MINE);

    private static JsonNode json(String raw) {
        return JSON.readTree(raw.replace('\'', '"'));
    }

    @Test
    void acceptsImagesAndTextAndSummarisesThem() {
        DesignCanvasValidator.Summary s = DesignCanvasValidator.validate(json(
                "{'version':'7.0.0','objects':[{'type':'Image','src':'" + MINE + "','left':10,'top':20,'scaleX':0.5},"
                        + "{'type':'IText','text':'Hello','fontFamily':'Anton','fill':'#ffffff','styles':[]}]}"), OWN);
        assertThat(s.objects()).isEqualTo(2);
        assertThat(s.images()).isEqualTo(1);
        assertThat(s.texts()).isEqualTo(1);
        assertThat(s.imageSources()).containsExactly(MINE);
    }

    @Test
    void emptySidesAreFine() {
        assertThat(DesignCanvasValidator.validate(null, OWN)).isEqualTo(DesignCanvasValidator.Summary.EMPTY);
        assertThat(DesignCanvasValidator.validate(json("{'objects':[]}"), OWN).objects()).isZero();
        assertThat(DesignCanvasValidator.validate(json("{'version':'7'}"), OWN).objects()).isZero();
    }

    @Test
    void rejectsForeignOrExternalImages() {
        assertInvalid("{'objects':[{'type':'Image','src':'/media/designs/99999999-2222-3333-4444-555555555555.png'}]}");
        assertInvalid("{'objects':[{'type':'Image','src':'https://evil.example/track.png'}]}");
        assertInvalid("{'objects':[{'type':'Image'}]}");
    }

    @Test
    void rejectsUnsupportedObjectsAndHiddenUrls() {
        assertInvalid("{'objects':[{'type':'Path','path':[]}]}");
        assertInvalid("{'objects':[{'type':'Rect'}]}");
        assertInvalid("{'objects':[{'type':'IText','text':'x','fill':{'source':'javascript:alert(1)'}}]}");
        assertInvalid("{'objects':[{'type':'IText','text':'x','backgroundImage':'data:image/svg+xml;base64,AAAA'}]}");
        assertInvalid("{'objects':[{'type':'IText','text':'x','clipPath':{'type':'Rect'}}]}");
        assertInvalid("{'objects':[{'type':'IText','text':'x','nested':{'src':'https://evil.example/a.png'}}]}");
        assertInvalid("[1,2,3]");
        assertInvalid("{'objects':{'type':'IText'}}");
    }

    @Test
    void limitsObjectCount() {
        StringBuilder many = new StringBuilder("{'objects':[");
        for (int i = 0; i <= DesignCanvasValidator.MAX_OBJECTS; i++) {
            many.append(i == 0 ? "" : ",").append("{'type':'IText','text':'x'}");
        }
        assertInvalid(many.append("]}").toString());
    }

    private static void assertInvalid(String raw) {
        assertThatThrownBy(() -> DesignCanvasValidator.validate(json(raw), OWN))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_DESIGN"));
    }
}
