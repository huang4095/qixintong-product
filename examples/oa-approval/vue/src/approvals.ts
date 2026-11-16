export type Decision = 'APPROVED' | 'REJECTED'
export type ApprovalStatus = 'PENDING' | Decision

export interface Approval {
  id: number
  title: string
  applicant: string
  status: ApprovalStatus
}

const endpoint = '/api/approvals'

async function request<T>(path: string, fields?: Record<string, string>): Promise<T> {
  const response = await fetch(path, fields ? {
    method: 'POST',
    body: new URLSearchParams(fields),
  } : undefined)

  if (!response.ok) {
    const result = await response.json().catch(() => ({}))
    if (typeof result.error === 'string' && result.error) {
      throw new Error(result.error)
    }
    throw new Error('请求失败，请确认 Java 服务已启动。')
  }

  return response.json()
}

export function listApprovals(): Promise<Approval[]> {
  return request(endpoint)
}

export function submitApproval(title: string, applicant: string): Promise<Approval> {
  return request(endpoint, { title, applicant })
}

export function decideApproval(id: number, decision: Decision): Promise<Approval> {
  return request(`${endpoint}/${id}/decision`, { decision })
}
