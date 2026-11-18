// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EditItemDialog from './EditItemDialog.vue'

const stubs = ['v-dialog', 'v-card', 'v-card-title', 'v-card-text', 'v-card-actions', 'v-alert', 'v-text-field', 'v-switch', 'v-spacer', 'v-btn', 'v-textarea']
const item = { codigo: 'PAPEL', descricao: 'Papel', unidade: 'UN', saldo: '1.000', ativo: true, versao: '9007199254740993' }

afterEach(() => vi.unstubAllGlobals())

describe('edição de item', () => {
  it('valida saldo antes de chamar a API e preserva a versão grande no JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(EditItemDialog, { propsData: { item }, stubs })
    const vm = wrapper.vm as any

    vm.draft.saldo = '-1'
    await vm.save()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(vm.error).toBeTruthy()

    vm.draft.descricao = ' Papel A4 '
    vm.draft.saldo = '0,001'
    await vm.save()
    expect(fetchMock).toHaveBeenCalledWith('/api/itens/PAPEL', expect.objectContaining({ method: 'PUT' }))
    expect(fetchMock.mock.calls[0][1].body).toBe('{"descricao":"Papel A4","unidade":"UN","saldo":0.001,"ativo":true,"versao":9007199254740993}')
    expect(wrapper.emitted('saved')).toHaveLength(1)
    wrapper.destroy()
  })

  it('impede novo salvamento após conflito até recarregar o item', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('{"mensagem":"Conflito"}', { status: 409, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...item, descricao: 'Atualizado', versao: '9007199254740994' }), { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(EditItemDialog, { propsData: { item }, stubs })
    const vm = wrapper.vm as any

    await vm.save()
    expect(vm.needsReload).toBe(true)
    await vm.save()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await vm.reload()
    expect(vm.needsReload).toBe(false)
    expect(vm.draft.descricao).toBe('Atualizado')
    wrapper.destroy()
  })
})
