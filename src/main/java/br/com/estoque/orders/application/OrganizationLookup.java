package br.com.estoque.orders.application;

public interface OrganizationLookup {
    boolean exists(String code);
}
