package br.com.estoque.organizations.infrastructure;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.java.StringTypeDescriptor;
import org.hibernate.type.descriptor.sql.CharTypeDescriptor;

/** Mapeia String para CHAR, preservando o tipo de UF definido na migração. */
public class FixedCharStringType extends AbstractSingleColumnStandardBasicType<String> {
    public FixedCharStringType() {super(CharTypeDescriptor.INSTANCE,StringTypeDescriptor.INSTANCE);}
    @Override public String getName() {return "fixed-char-string";}
}
