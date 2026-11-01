package br.com.cfc.shared;

import br.com.cfc.shared.validation.Documentos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosTest {

    @Test
    void cpf() {
        assertThat(Documentos.cpfValido("529.982.247-25")).isTrue();
        assertThat(Documentos.cpfValido("52998224724")).isFalse();
        assertThat(Documentos.cpfValido("111.111.111-11")).isFalse();
        assertThat(Documentos.cpfValido("123")).isFalse();
    }

    @Test
    void cnpjNumericoEAlfanumerico() {
        assertThat(Documentos.cnpjValido("11.222.333/0001-81")).isTrue();
        assertThat(Documentos.cnpjValido("11.222.333/0001-82")).isFalse();
        assertThat(Documentos.cnpjValido("12.ABC.345/01DE-35")).isTrue(); // exemplo oficial da Receita
        assertThat(Documentos.cnpjValido("12abc34501de35")).isTrue();
        assertThat(Documentos.cnpjValido("12ABC34501DE36")).isFalse();
        assertThat(Documentos.cnpjValido("00000000000000")).isFalse();
    }
}
