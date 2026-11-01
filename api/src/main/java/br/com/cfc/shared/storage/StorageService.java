package br.com.cfc.shared.storage;

/**
 * Onde ficam os arquivos (documentos de alunos, contratos). Hoje é uma pasta em disco;
 * para trocar por S3 basta outra implementação desta interface.
 */
public interface StorageService {

    void salvar(String caminho, byte[] dados);

    byte[] ler(String caminho);

    boolean existe(String caminho);

    void apagar(String caminho);
}
