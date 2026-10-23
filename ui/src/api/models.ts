import { get, post, put, request } from './client'

export type ModelProvider = 'deepseek' | 'zhipuai' | 'openai'
export interface ModelConfiguration {
  id: string
  name: string
  provider: ModelProvider
  model: string
  baseUrl: string
  hasApiKey: boolean
  revision: number
  active: boolean
}
export interface ModelSettings { configurations: ModelConfiguration[]; activeId: string | null }
export interface ModelInput {
  name: string; provider: ModelProvider; model: string; baseUrl: string
  apiKey?: string; revision?: number
}
export interface ModelTestResult { structuredOutput: boolean; toolCalling: boolean; message: string }
export const fetchModelSettings = () => get<ModelSettings>('/api/model-settings')
export const saveModelConfiguration = (id: string | undefined, input: ModelInput) =>
  id ? put<ModelConfiguration>(`/api/model-settings/${id}`, input) : post<ModelConfiguration>('/api/model-settings', input)
export const activateModel = (id: string) => put<void>('/api/model-settings/active', { id })
export const deleteModel = (configuration: ModelConfiguration) =>
  request<void>('DELETE', `/api/model-settings/${configuration.id}?revision=${configuration.revision}`)
export const testModel = (id: string | undefined, configuration: ModelInput) =>
  post<ModelTestResult>('/api/model-settings/test', { id, configuration })
export function notifyModelSettingsChanged() { window.dispatchEvent(new Event('prism:model-settings-changed')) }

