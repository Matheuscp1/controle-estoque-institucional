<template>
  <div>
    <div class="page-heading">
      <h1>Pedidos por órgão</h1>
      <p>Consulte os pedidos e os itens solicitados por cada órgão.</p>
    </div>

    <form class="panel filter-panel" aria-label="Filtros de pedidos" @submit.prevent="applyFilters">
      <h2>Filtrar pedidos</h2>
      <v-alert v-if="organizationError" class="mt-4 mb-0" type="error" text role="alert">
        {{ organizationError }}
        <v-btn text small color="error" @click="loadOrganizations">Tentar novamente</v-btn>
      </v-alert>
      <div class="filter-grid">
        <v-autocomplete
          v-model="draft.codigo"
          :items="organizations"
          :loading="loadingOrganizations"
          :disabled="loadingOrganizations || !!organizationError"
          label="Órgão"
          item-text="label"
          item-value="codigo"
          no-data-text="Nenhum órgão encontrado"
          outlined dense hide-details
        />
        <label class="date-filter">
          <span>Data inicial</span>
          <input v-model="draft.firstDay" type="date" />
        </label>
        <label class="date-filter">
          <span>Data final</span>
          <input v-model="draft.lastDay" type="date" />
        </label>
        <v-select v-model="draft.situacao" :items="statuses" label="Situação" outlined dense hide-details />
      </div>
      <p v-if="filterError" class="field-error" role="alert">{{ filterError }}</p>
      <div class="filter-actions">
        <v-btn :disabled="loadingOrganizations || !!organizationError" color="primary" type="submit" depressed>
          <v-icon left small>mdi-magnify</v-icon> Buscar
        </v-btn>
        <v-btn type="button" text @click="clearFilters">Limpar filtros</v-btn>
      </div>
    </form>

    <CursorList
      v-if="filters"
      ref="results"
      title="Pedidos encontrados"
      empty-message="Nenhum pedido encontrado para este órgão com os filtros informados."
      total-label="Total de pedidos"
      :filters="filters"
      :fetch-page="fetchOrders"
      cursor-key="id"
    >
      <template #default="{ rows }">
        <v-simple-table class="data-table">
          <template #default>
            <thead>
              <tr>
                <th scope="col">Identificador</th>
                <th scope="col">Solicitante</th>
                <th scope="col">Data do pedido</th>
                <th scope="col">Situação</th>
                <th scope="col" class="text-right">Ações</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="order in rows">
                <tr :key="order.id">
                  <td><span class="record-code">{{ order.idExterno }}</span></td>
                  <td>{{ order.solicitante }}</td>
                  <td class="numeric">{{ formatDate(order.dataHora) }}</td>
                  <td>
                    <v-chip small :class="order.situacao === 'CONFIRMADO' ? 'status-active' : 'status-inactive'">
                      {{ order.situacao === 'CONFIRMADO' ? 'Confirmado' : 'Cancelado' }}
                    </v-chip>
                  </td>
                  <td class="text-right order-actions">
                    <v-btn
                      small text color="primary"
                      :aria-expanded="expandedId === order.id ? 'true' : 'false'"
                      :aria-label="'Itens do pedido ' + order.idExterno"
                      @click="expandedId = expandedId === order.id ? null : order.id"
                    >
                      Itens ({{ order.itens.length }})
                      <v-icon right small>{{ expandedId === order.id ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
                    </v-btn>
                    <v-btn
                      v-if="order.situacao === 'CONFIRMADO'"
                      small text color="error"
                      :disabled="importReadiness.ready === false"
                      :title="importReadiness.ready === false ? 'Cancelamento disponível após a validação da carga' : ''"
                      :aria-label="'Cancelar pedido ' + order.idExterno"
                      @click="cancelling = order"
                    >
                      Cancelar
                    </v-btn>
                  </td>
                </tr>
                <tr v-if="expandedId === order.id" :key="order.id + '-details'" class="order-detail">
                  <td colspan="5">
                    <v-simple-table dense class="order-lines">
                      <template #default>
                        <thead>
                          <tr>
                            <th scope="col">Código do item</th>
                            <th scope="col" class="text-right">Quantidade</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="line in order.itens" :key="line.codigoItem">
                            <td>{{ line.codigoItem }}</td>
                            <td class="text-right numeric">{{ formatDecimal(line.quantidade) }}</td>
                          </tr>
                        </tbody>
                      </template>
                    </v-simple-table>
                    <p v-if="order.dataCancelamento" class="cancellation-detail">
                      Cancelado em {{ formatDate(order.dataCancelamento) }}.
                      <template v-if="order.motivoCancelamento">Motivo: {{ order.motivoCancelamento }}</template>
                    </p>
                  </td>
                </tr>
              </template>
            </tbody>
          </template>
        </v-simple-table>
      </template>
    </CursorList>
    <section v-else-if="!loadingOrganizations && !organizationError" class="panel">
      <p class="results-message">{{ organizations.length
        ? 'Selecione um órgão e clique em Buscar para consultar seus pedidos.'
        : 'Nenhum órgão cadastrado.' }}</p>
    </section>
    <CancelOrderDialog
      v-if="cancelling"
      :order="cancelling"
      @close="cancelling = null"
      @cancelled="onCancelled"
      @changed="refreshOrders"
    />
    <v-snackbar v-model="cancelled" color="success" :timeout="4000">
      Pedido cancelado. O saldo dos itens foi atualizado.
      <template #action="{ attrs }">
        <v-btn v-bind="attrs" text @click="cancelled = false">Fechar</v-btn>
      </template>
    </v-snackbar>
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import CursorList from '../../components/CursorList.vue'
import CancelOrderDialog from './components/CancelOrderDialog.vue'
import { errorMessage, importReadiness, request, type Order, type Organization, type PageResult } from '../../services/api'
import { formatDecimal } from '../../utils/decimal'

const emptyFilters = () => ({ codigo: null, firstDay: '', lastDay: '', situacao: '' })
const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export default Vue.extend({
  components: { CursorList, CancelOrderDialog },
  data: () => ({
    draft: emptyFilters(),
    filters: null,
    statuses: [
      { text: 'Todas', value: '' },
      { text: 'Confirmado', value: 'CONFIRMADO' },
      { text: 'Cancelado', value: 'CANCELADO' },
    ],
    organizations: [],
    organizationError: '',
    loadingOrganizations: false,
    pendingOrganizations: null,
    filterError: '',
    expandedId: null,
    cancelling: null,
    cancelled: false,
    importReadiness,
  }),
  created() {
    this.loadOrganizations()
  },
  beforeDestroy() {
    this.pendingOrganizations?.abort()
  },
  methods: {
    formatDecimal,
    formatDate(value) {
      return dateFormat.format(new Date(value))
    },
    async loadOrganizations() {
      this.loadingOrganizations = true
      this.organizationError = ''
      const pending = new AbortController()
      this.pendingOrganizations = pending
      const organizations = []
      const limite = 100
      try {
        let depois
        let page
        do {
          page = await request<Organization[]>('/orgaos', { params: { depois, limite }, signal: pending.signal })
          organizations.push(...page.map((org) => ({
            codigo: org.codigo,
            label: `${org.sigla} · ${org.nome} (${org.codigo})`,
          })))
          depois = page[page.length - 1]?.codigo
        } while (page.length === limite)
        this.organizations = organizations
      } catch (error) {
        if (!pending.signal.aborted) this.organizationError = errorMessage(error)
      } finally {
        this.loadingOrganizations = false
        this.pendingOrganizations = null
      }
    },
    fetchOrders({ codigo, ...filters }, antes, limite, signal) {
      return request<PageResult<Order>>(`/orgaos/${encodeURIComponent(codigo)}/pedidos`, {
        params: { ...filters, antes, limite },
        signal,
      })
    },
    applyFilters() {
      this.filterError = ''
      const { codigo, firstDay, lastDay, situacao } = this.draft
      if (!codigo) {
        this.filterError = 'Selecione um órgão.'
        return
      }
      if (firstDay && lastDay && firstDay > lastDay) {
        this.filterError = 'A data inicial deve ser anterior ou igual à data final.'
        return
      }
      const inicio = firstDay ? new Date(`${firstDay}T00:00:00`).toISOString() : ''
      const end = lastDay ? new Date(`${lastDay}T00:00:00`) : null
      if (end) {
        end.setDate(end.getDate() + 1)
        end.setHours(0, 0, 0, 0)
      }
      this.expandedId = null
      this.filters = { codigo, inicio, fim: end?.toISOString(), situacao }
    },
    clearFilters() {
      this.draft = emptyFilters()
      this.filters = null
      this.filterError = ''
      this.expandedId = null
    },
    refreshOrders() {
      this.$refs.results.refresh()
    },
    onCancelled() {
      this.cancelling = null
      this.cancelled = true
      this.refreshOrders()
    },
  },
})
</script>
