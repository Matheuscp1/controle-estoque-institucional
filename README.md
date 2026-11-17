# controle-estoque-institucional

Estrutura inicial do backend em Java 17 e Spring Boot 2.7.18.

Projeto Maven: `br.com.estoque:controle-estoque-institucional`.
Pacote Java: `br.com.estoque`.
Repositório: https://github.com/Matheuscp1/controle-estoque-institucional

## Estrutura

- `pom.xml`: dependências e build Maven.
- `src/main/java/br/com/estoque/Application.java`: entrada da aplicação.
- `src/main/resources/application.yml`: configuração do banco e da API.
- `Dockerfile` e `compose.yaml`: aplicação e PostgreSQL 15.

## Executar

Com Docker em execução:

```sh
docker compose up --build
```

A aplicação usa a porta `8080`. Esta estrutura ainda não possui endpoints de negócio ou migrações.
O banco usa volume persistente. A senha padrão é apenas para desenvolvimento; configure `DB_PASSWORD` em `.env` para alterá-la.

Para compilar com Java 17 e Maven:

```sh
mvn package
```

Para executar sem Docker, disponibilize PostgreSQL, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` e execute `mvn spring-boot:run`.

## Próximas etapas

Implementar itens, pedidos, controle de concorrência, carga histórica e testes conforme o enunciado.
