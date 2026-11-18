<template>
  <section class="panel results" :aria-busy="loading ? 'true' : 'false'">
    <div class="results-title">
      <h2>{{ title }}</h2>
      <span v-if="rows.length">{{ rows.length }} nesta página</span>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" aria-label="Carregando resultados" />
    <v-alert v-if="error" class="results-message" type="error" text role="alert">
      {{ error }}
      <v-btn text small color="error" @click="retry">Tentar novamente</v-btn>
    </v-alert>
    <slot v-if="rows.length" :rows="rows" />
    <p v-else-if="!loading && !error" class="results-message" role="status">{{ emptyMessage }}</p>
    <div v-if="page || canAdvance" class="pagination">
      <span aria-live="polite">Página {{ page + 1 }}</span>
      <v-btn outlined small :disabled="loading || page === 0" @click="load(page - 1)">
        <v-icon left small>mdi-chevron-left</v-icon> Anterior
      </v-btn>
      <v-btn outlined small :disabled="loading || !canAdvance" @click="advance">
        Próxima <v-icon right small>mdi-chevron-right</v-icon>
      </v-btn>
    </div>
  </section>
</template>

<script lang="ts">
import Vue from 'vue'
import { errorMessage } from '../services/api'

export default Vue.extend({
  props: {
    title: { type: String, required: true },
    emptyMessage: { type: String, required: true },
    filters: { type: Object, required: true },
    fetchPage: { type: Function, required: true },
    cursorKey: { type: String, required: true },
    limit: { type: Number, default: 50 },
  },
  data: () => ({
    rows: [],
    cursors: [null],
    page: 0,
    canAdvance: false,
    loading: false,
    error: '',
    retryPage: 0,
    pending: null,
  }),
  watch: {
    filters() {
      this.rows = []
      this.cursors = [null]
      this.page = 0
      this.canAdvance = false
      this.load(0)
    },
  },
  created() {
    this.load(0)
  },
  beforeDestroy() {
    this.pending?.abort()
    this.pending = null
  },
  methods: {
    refresh() {
      return this.load(this.page)
    },
    retry() {
      this.load(this.retryPage)
    },
    advance() {
      this.cursors.splice(this.page + 1, 1, this.rows[this.rows.length - 1][this.cursorKey])
      this.load(this.page + 1)
    },
    async load(page) {
      this.pending?.abort()
      const pending = new AbortController()
      this.pending = pending
      this.retryPage = page
      this.error = ''
      this.loading = true

      try {
        const rows = await this.fetchPage(this.filters, this.cursors[page], this.limit, pending.signal)
        if (this.pending !== pending) return
        if (!rows.length && page > this.page) {
          this.canAdvance = false
          return
        }
        this.rows = rows
        this.page = page
        this.canAdvance = rows.length === this.limit
      } catch (error) {
        if (this.pending === pending) this.error = errorMessage(error)
      } finally {
        if (this.pending === pending) {
          this.pending = null
          this.loading = false
        }
      }
    },
  },
})
</script>
