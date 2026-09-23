import { describe, it, expect, vi } from 'vitest'
import { shallowMount, flushPromises, config } from '@vue/test-utils'
import McpManage from '@/views/McpManage.vue'
import JsonInput from '@/components/JsonInput.vue'
import LbDialogFooter from '@/components/common/LbDialogFooter.vue'
import { getMcpServers, updateMcpServer } from '@/api/mcp'

vi.mock('@/api/mcp', () => ({
  getMcpServers: vi.fn(),
  createMcpServer: vi.fn(),
  updateMcpServer: vi.fn(),
  deleteMcpServer: vi.fn(),
  testMcpServer: vi.fn(),
  getMcpServerTools: vi.fn(),
  refreshMcpServerTools: vi.fn(),
  toggleMcpTool: vi.fn(),
  setMcpServerEnabled: vi.fn(),
}))

vi.mock('ant-design-vue', async (importOriginal) => {
  const original = await importOriginal()
  return { ...original, message: { warning: vi.fn(), success: vi.fn() }, Modal: { confirm: vi.fn() } }
})

config.global.renderStubDefaultSlot = true

function remote(id, headers, deployConfig = null) {
  return {
    id,
    name: `Remote ${id}`,
    installType: 'sse',
    transport: 'streamable_http',
    host: `https://server-${id}.example.invalid/mcp`,
    headers,
    deployConfig,
  }
}

function mountManage() {
  updateMcpServer.mockClear()
  getMcpServers.mockResolvedValue({ data: { records: [] } })
  updateMcpServer.mockResolvedValue({ data: {} })
  return shallowMount(McpManage, {
    global: {
      stubs: {
        ...Object.fromEntries(
          [
            'a-tooltip',
            'a-menu-item',
            'a-menu',
            'a-dropdown',
            'a-spin',
            'a-input',
            'a-form-item',
            'a-select-option',
            'a-select',
            'a-textarea',
            'a-form',
            'a-tag',
            'a-descriptions-item',
            'a-descriptions',
            'a-switch',
            'a-drawer',
          ].map((name) => [name, true])
        ),
        'a-modal': { template: '<div><slot /><slot name="footer" /></div>' },
        JsonInput: {
          props: ['modelValue'],
          template:
            '<textarea data-test="headers" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        LbDialogFooter: { template: '<button data-test="save" @click="$emit(\'confirm\')">Save</button>' },
      },
    },
  })
}

async function save(wrapper) {
  wrapper.findAllComponents(LbDialogFooter)[0].vm.$emit('confirm')
  await flushPromises()
  expect(updateMcpServer).toHaveBeenCalledTimes(1)
  return updateMcpServer.mock.calls[0][0]
}

describe('MCP server header editing', () => {
  it('preserves the current server header through the actual edit and save flow', async () => {
    const wrapper = mountManage()
    wrapper.vm.openDialog(remote('a', '{"Authorization":"Bearer TEST_A"}'))
    await flushPromises()
    expect(wrapper.findComponent(JsonInput).props('modelValue')).toBe('{"Authorization":"Bearer TEST_A"}')
    const request = await save(wrapper)
    expect(request.headers).toBe('{"Authorization":"Bearer TEST_A"}')
    expect(request.host).toBe('https://server-a.example.invalid/mcp')
    wrapper.unmount()
  })

  it('keeps two servers isolated and clears only an explicitly emptied header', async () => {
    const wrapper = mountManage()
    wrapper.vm.openDialog(remote('a', '{"Authorization":"Bearer TEST_A"}'))
    wrapper.vm.openDialog(remote('b', '{"Authorization":"Bearer TEST_B"}'))
    await flushPromises()
    expect(wrapper.findComponent(JsonInput).props('modelValue')).toBe('{"Authorization":"Bearer TEST_B"}')
    const request = await save(wrapper)
    expect(request.id).toBe('b')
    expect(request.host).toBe('https://server-b.example.invalid/mcp')
    expect(request.headers).toBe('{"Authorization":"Bearer TEST_B"}')

    updateMcpServer.mockClear()
    wrapper.vm.openDialog(remote('c', '{"X-API-Key":"TEST_C"}'))
    await flushPromises()
    expect((await save(wrapper)).headers).toBe('{"X-API-Key":"TEST_C"}')

    updateMcpServer.mockClear()
    wrapper.vm.openDialog(remote('empty', null))
    await flushPromises()
    expect(wrapper.findComponent(JsonInput).props('modelValue')).toBe('{}')
    expect((await save(wrapper)).headers).toBe('{}')

    updateMcpServer.mockClear()
    wrapper.vm.openDialog(remote('blank', ''))
    await flushPromises()
    expect(wrapper.findComponent(JsonInput).props('modelValue')).toBe('{}')
    expect((await save(wrapper)).headers).toBe('{}')
    wrapper.unmount()
  })

  it('sends null only after the current server header is deliberately cleared', async () => {
    const wrapper = mountManage()
    wrapper.vm.openDialog(remote('a', '{"Authorization":"Bearer TEST_A"}'))
    await flushPromises()
    wrapper.findComponent(JsonInput).vm.$emit('update:modelValue', '')
    await flushPromises()
    expect((await save(wrapper)).headers).toBeNull()
    wrapper.unmount()
  })

  it('uses a legacy deployConfig header only when the separate header field is absent', async () => {
    const wrapper = mountManage()
    const legacy = remote('legacy', undefined, '{"headers":{"Authorization":"Bearer LEGACY"}}')
    delete legacy.headers
    wrapper.vm.openDialog(legacy)
    await flushPromises()
    expect(JSON.parse(wrapper.findComponent(JsonInput).props('modelValue'))).toEqual({ Authorization: 'Bearer LEGACY' })
    expect(JSON.parse((await save(wrapper)).headers)).toEqual({ Authorization: 'Bearer LEGACY' })

    updateMcpServer.mockClear()
    wrapper.vm.openDialog(remote('empty', null, legacy.deployConfig))
    await flushPromises()
    expect((await save(wrapper)).headers).toBe('{}')

    updateMcpServer.mockClear()
    const absent = remote('absent', undefined)
    delete absent.headers
    wrapper.vm.openDialog(absent)
    await flushPromises()
    expect((await save(wrapper)).headers).toBe('{}')
    wrapper.unmount()
  })
})
