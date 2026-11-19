<template>
  <v-app>
    <header class="topbar">
      <div class="topbar-inner">
        <a class="brand" href="#itens">
          <span class="brand-icon"><v-icon dark>mdi-package-variant-closed</v-icon></span>
          Estoque Institucional
        </a>
        <nav class="main-nav" aria-label="Navegação principal">
          <a href="#itens" :aria-current="section === 'itens' ? 'page' : null">
            <v-icon small>mdi-view-list-outline</v-icon> Itens
          </a>
          <a href="#pedidos" :aria-current="section === 'pedidos' ? 'page' : null">
            <v-icon small>mdi-clipboard-list-outline</v-icon> Pedidos
          </a>
        </nav>
      </div>
    </header>
    <v-main>
      <main class="workspace">
        <ImportStatusPanel v-if="importReadiness.ready === false" />
        <ItemsView v-if="section === 'itens'" />
        <OrdersView v-else />
      </main>
    </v-main>
  </v-app>
</template>

<script lang="ts">
import Vue from 'vue'
import ItemsView from './views/items/ItemsView.vue'
import OrdersView from './views/orders/OrdersView.vue'
import ImportStatusPanel from './components/ImportStatusPanel.vue'
import { importReadiness } from './services/api'

function currentSection() {
  return window.location.hash === '#pedidos' ? 'pedidos' : 'itens'
}

export default Vue.extend({
  components: { ItemsView, OrdersView, ImportStatusPanel },
  data: () => ({ section: currentSection(), importReadiness }),
  mounted() {
    window.addEventListener('hashchange', this.updateSection)
  },
  beforeDestroy() {
    window.removeEventListener('hashchange', this.updateSection)
  },
  methods: {
    updateSection() {
      this.section = currentSection()
    },
  },
})
</script>
