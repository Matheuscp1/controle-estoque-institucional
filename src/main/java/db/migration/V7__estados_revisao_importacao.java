package db.migration;

import java.sql.SQLException;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__estados_revisao_importacao extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();var names=new ArrayList<String>();
        try(var query=connection.prepareStatement("""
            SELECT t.constraint_name FROM information_schema.table_constraints t
            JOIN information_schema.check_constraints c ON c.constraint_catalog=t.constraint_catalog
             AND c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
            WHERE LOWER(t.table_name)='import_jobs' AND t.table_schema=? AND t.constraint_type='CHECK'
              AND LOWER(c.check_clause) LIKE '%status%' AND LOWER(c.check_clause) LIKE '%preparing%'
            """)) {
            query.setString(1,connection.getSchema());
            try(var rows=query.executeQuery()){while(rows.next())names.add(rows.getString(1));}
        }
        if(names.size()!=1)throw new SQLException("Esperado um CHECK de status em import_jobs.");
        try(var statement=connection.createStatement()) {
            statement.execute("ALTER TABLE import_jobs DROP CONSTRAINT \""+names.get(0).replace("\"","\"\"")+"\"");
            statement.execute("ALTER TABLE import_jobs ADD CONSTRAINT ck_import_jobs_status CHECK (status IN ('PREPARING','STAGING','COMPLETED','NEEDS_REVIEW','FAILED','COMPLETED_WITH_ISSUES','REVIEW_PENDING'))");
        }
    }
}
