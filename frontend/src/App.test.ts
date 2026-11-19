// @vitest-environment jsdom
import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { importReadiness } from './services/api'
import App from './App.vue'
import ImportStatusPanel from './components/ImportStatusPanel.vue'

afterEach(() => { importReadiness.ready = null })

describe('estado da carga na aplicação', () => {
  it('mostra o painel durante a carga e o remove quando a API libera operações', async () => {
    importReadiness.ready = false
    const wrapper = shallowMount(App, { stubs: ['v-app', 'v-main', 'v-icon'] })

    expect(wrapper.findComponent(ImportStatusPanel).exists()).toBe(true)
    importReadiness.ready = true
    await Vue.nextTick()
    expect(wrapper.findComponent(ImportStatusPanel).exists()).toBe(false)
    wrapper.destroy()
  })
})
