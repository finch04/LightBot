import { describe, it, expect } from 'vitest'
import { displayNameToPageType, isValidPageType } from '@/utils/pageTypeSlug'

describe('displayNameToPageType', () => {
  it('converts Chinese display name to snake_case pinyin', () => {
    expect(displayNameToPageType('水电燃气缴费')).toBe('shui_di_ran_qi_jiao_fei')
    expect(displayNameToPageType('请假申请')).toBe('qing_jia_shen_qing')
  })

  it('keeps English words as snake_case', () => {
    expect(displayNameToPageType('Utility Bill Pay')).toBe('utility_bill_pay')
    expect(displayNameToPageType('leave_request')).toBe('leave_request')
  })

  it('handles mixed Chinese and English', () => {
    expect(displayNameToPageType('水电 Bill')).toBe('shui_di_bill')
  })

  it('prefixes leading digits', () => {
    expect(displayNameToPageType('123测试')).toMatch(/^p_/)
    expect(isValidPageType(displayNameToPageType('123测试'))).toBe(true)
  })

  it('returns empty for blank input', () => {
    expect(displayNameToPageType('')).toBe('')
    expect(displayNameToPageType('   ')).toBe('')
  })

  it('falls back when no usable chars', () => {
    expect(displayNameToPageType('!!!')).toBe('business_page')
  })
})

describe('isValidPageType', () => {
  it('accepts snake_case codes', () => {
    expect(isValidPageType('utility_bill_pay')).toBe(true)
    expect(isValidPageType('a')).toBe(true)
  })

  it('rejects invalid formats', () => {
    expect(isValidPageType('Utility')).toBe(false)
    expect(isValidPageType('1abc')).toBe(false)
    expect(isValidPageType('a-b')).toBe(false)
    expect(isValidPageType('')).toBe(false)
  })
})
