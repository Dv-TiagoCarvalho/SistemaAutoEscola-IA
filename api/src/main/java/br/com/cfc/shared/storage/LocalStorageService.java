package br.com.cfc.shared.storage;

import br.com.cfc.shared.exception.NaoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Service
public class LocalStorageService implements StorageService {

    private final Path base;

    public LocalStorageService(@Value("${app.storage.path:./uploads}") String pasta) throws IOException {
        this.base = Path.of(pasta).toAbsolutePath().normalize();
        Files.createDirectories(base);
    }

    @Override
    public void salvar(String caminho, byte[] dados) {
        try {
            Path alvo = resolver(caminho);
            Files.createDirectories(alvo.getParent());
            Files.write(alvo, dados);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o arquivo.", e);
        }
    }

    @Override
    public byte[] ler(String caminho) {
        try {
            return Files.readAllBytes(resolver(caminho));
        } catch (NoSuchFileException e) {
            throw new NaoEncontradoException("Arquivo não encontrado.");
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo.", e);
        }
    }

    @Override
    public boolean existe(String caminho) {
        return Files.isRegularFile(resolver(caminho));
    }

    @Override
    public void apagar(String caminho) {
        try {
            Files.deleteIfExists(resolver(caminho));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível apagar o arquivo.", e);
        }
    }

    /** Impede "../" e caminhos absolutos: o arquivo sempre fica dentro da pasta base. */
    private Path resolver(String caminho) {
        Path alvo = base.resolve(caminho).normalize();
        if (!alvo.startsWith(base)) {
            throw new IllegalArgumentException("Caminho inválido.");
        }
        return alvo;
    }
}
