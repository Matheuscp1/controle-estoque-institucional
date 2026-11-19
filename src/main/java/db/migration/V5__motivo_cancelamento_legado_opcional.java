package db.migration;

import java.sql.SQLException;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** A V1 não nomeou o CHECK; PostgreSQL e H2 geram nomes diferentes. */
public class V5__motivo_cancelamento_legado_opcional extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        var constraints=new ArrayList<String>();
        try(var query=connection.prepareStatement("""
            SELECT t.constraint_name FROM information_schema.table_constraints t
            JOIN information_schema.check_constraints c
              ON c.constraint_catalog=t.constraint_catalog AND c.constraint_schema=t.constraint_schema
             AND c.constraint_name=t.constraint_name
            WHERE LOWER(t.table_name)='pedidos' AND t.table_schema=? AND t.constraint_type='CHECK'
              AND LOWER(c.check_clause) LIKE '%motivo_cancelamento%'
              AND LOWER(c.check_clause) LIKE '%situacao%'
            """)) {
            query.setString(1,connection.getSchema());
            try(var rows=query.executeQuery()) {while(rows.next())constraints.add(rows.getString(1));}
        }
        if(constraints.size()!=1) throw new SQLException("Esperado um CHECK de cancelamento em pedidos; encontrados: "+constraints.size());
        try(var statement=connection.createStatement()) {
            statement.execute("ALTER TABLE pedidos DROP CONSTRAINT \""+constraints.get(0).replace("\"","\"\"")+"\"");
            statement.execute("""
                ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_cancelamento CHECK (
                  (situacao='CONFIRMADO' AND data_cancelamento IS NULL AND motivo_cancelamento IS NULL)
                  OR (situacao='CANCELADO' AND data_cancelamento IS NOT NULL AND data_cancelamento>=data_hora
                    AND (motivo_cancelamento IS NULL OR TRIM(motivo_cancelamento)<>'')))
                """);
        }
    }
}
