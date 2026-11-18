<template>
  <div>
    <div class="page-heading">
      <h1>Consulta de itens</h1>
      <p>Consulte o catálogo e acompanhe o saldo disponível.</p>
    </div>

    <form class="panel filter-panel" aria-label="Filtros de itens" @submit.prevent="applyFilters">
      <h2>Filtrar itens</h2>
      <div class="filter-grid">
        <v-text-field v-model="draft.descricao" label="Descrição" placeholder="Ex.: Papel A4" outlined dense hide-details />
        <v-text-field v-model="draft.unidade" label="Unidade de medida" placeholder="Ex.: UN" outlined dense hide-details />
        <v-text-field v-model="draft.saldoMin" label="Saldo mínimo" inputmode="decimal" outlined dense hide-details />
        <v-text-field v-model="draft.saldoMax" label="Saldo máximo" inputmode="decimal" outlined dense hide-details />
      </div>
      <p v-if="filterError" class="field-error" role="alert">{{ filterError }}</p>
      <div class="filter-actions">
        <v-btn color="primary" type="submit" depressed><v-icon left small>mdi-magnify</v-icon> Buscar</v-btn>
        <v-btn type="button" text @click="clearFilters">Limpar filtros</v-btn>
      </div>
    </form>

    <CursorList
      title="Itens do catálogo"
      empty-message="Nenhum item encontrado com os filtros informados."
      :filters="filters"
      :fetch-page="fetchItems"
      cursor-key="codigo"
    >
      <template #default="{ rows }">
        <v-simple-table class="data-table">
          <template #default>
            <thead>
              <tr>
                <th scope="col">Código</th>
                <th scope="col">Descrição</th>
                <th scope="col">Unidade</th>
                <th scope="col" class="text-right">Saldo disponível</th>
                <th scope="col">Situação</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in rows" :key="item.codigo">
                <td><span class="record-code">{{ item.codigo }}</span></td>
                <td>{{ item.descricao }}</td>
                <td>{{ item.unidade }}</td>
                <td class="text-right numeric">{{ formatDecimal(item.saldo) }}</td>
                <td>
                  <v-chip small :class="item.ativo ? 'status-active' : 'status-inactive'">
                    {{ item.ativo ? 'Ativo' : 'Inativo' }}
                  </v-chip>
                </td>
              </tr>
            </tbody>
          </template>
        </v-simple-table>
      </template>
    </CursorList>
  </div>
</template>

<script>
import CursorList from './CursorList.vue'
import { request } from './api'
import { decimalInput, decimalMilliunits, formatDecimal } from './decimal'

const emptyFilters = () => ({ descricao: '', unidade: '', saldoMin: '', saldoMax: '' })

export default {
  components: { CursorList },
  data: () => ({ draft: emptyFilters(), filters: {}, filterError: '' }),
  methods: {
    formatDecimal,
    fetchItems(filters, depois, limite, signal) {
      return request('/itens', { params: { ...filters, depois, limite }, signal })
    },
    applyFilters() {
      this.filterError = ''
      const { descricao, unidade, saldoMin, saldoMax } = this.draft
      const minimum = saldoMin.trim() ? decimalInput(saldoMin) : ''
      const maximum = saldoMax.trim() ? decimalInput(saldoMax) : ''
      if (minimum === null || maximum === null) {
        this.filterError = 'Informe saldos não negativos, com até 16 dígitos inteiros e três casas decimais.'
        return
      }
      if (minimum && maximum && decimalMilliunits(minimum) > decimalMilliunits(maximum)) {
        this.filterError = 'O saldo mínimo deve ser menor ou igual ao saldo máximo.'
        return
      }
      this.filters = { descricao: descricao.trim(), unidade: unidade.trim(), saldoMin: minimum, saldoMax: maximum }
    },
    clearFilters() {
      this.draft = emptyFilters()
      this.applyFilters()
    },
  },
}
</script>
