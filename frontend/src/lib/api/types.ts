// Contratos da API (espelham os DTOs do backend). Valores monetarios chegam como numero JSON (BRL).

export type Role = 'VIEWER' | 'ANALYST' | 'MANAGER' | 'ADMIN'

export interface Membership {
  organizationId: string
  organizationName: string
  demo: boolean
  role: Role
}
export interface Me {
  userId: string
  name: string
  email: string
  organizations: Membership[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Store { id: string; name: string; city: string | null }

export interface DateRange { from: string; to: string }
export interface SalesMetrics {
  revenue: number
  salesCount: number
  itemsSold: number
  averageTicket: number | null
  estimatedMargin: number | null
  marginPercent: number | null
}
export interface PeriodComparison {
  current: DateRange
  previous: DateRange
  currentMetrics: SalesMetrics
  previousMetrics: SalesMetrics
  revenueVariationPercent: number | null
  salesCountVariationPercent: number | null
  averageTicketVariationPercent: number | null
  note: string | null
}
export interface DashboardSummary { comparison: PeriodComparison; lowStockCount: number; unreadAlerts: number }
export interface SeriesPoint { date: string; revenue: number; salesCount: number }
export interface TopProduct { productId: string; sku: string; name: string; category: string; unitsSold: number; revenue: number }
export interface LowStockItem {
  storeId: string; storeName: string; productId: string; sku: string; productName: string
  category: string; quantity: number; minStock: number; deficit: number
}
export interface CoverageItem {
  storeId: string; storeName: string; productId: string; sku: string; productName: string; category: string
  quantity: number; minStock: number; unitsSoldInWindow: number; averageDailySales: number
  coverageDays: number | null; leadTimeDays: number; targetCoverageDays: number
}
export interface StaleProduct { productId: string; sku: string; name: string; category: string; totalStock: number; lastSaleDate: string | null }

export interface SaleRow { id: string; occurredAt: string; saleDate: string; storeId: string; storeName: string; total: number; itemsCount: number }
export interface SaleItemRow { productId: string; sku: string; productName: string; quantity: number; unitPrice: number; lineTotal: number }
export interface SaleDetail { sale: SaleRow; items: SaleItemRow[] }

export interface ProductRow {
  id: string; sku: string; name: string; category: string; cost: number; price: number
  minStock: number; targetCoverageDays: number; leadTimeDays: number; active: boolean; totalStock: number
}
export interface InventoryRow {
  storeId: string; storeName: string; productId: string; sku: string; productName: string
  category: string; quantity: number; minStock: number; belowMinimum: boolean
}
export interface MovementRow {
  id: string; occurredAt: string; storeId: string; storeName: string; productId: string; sku: string
  productName: string; type: string; quantityDelta: number; balanceAfter: number
  referenceType: string | null; referenceId: string | null
}

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export interface AlertDto {
  id: string; type: string; severity: AlertSeverity; storeId: string | null; productId: string | null
  title: string; message: string; read: boolean; createdAt: string
}

export type ActionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'EXECUTED' | 'REJECTED' | 'CANCELLED' | 'FAILED'
export interface ActionSummary {
  id: string; type: string; status: ActionStatus; storeId: string; storeName: string; createdByName: string
  createdViaAi: boolean; itemCount: number; totalUnits: number; createdAt: string; updatedAt: string
}
export interface ActionItem {
  productId: string; sku: string; productName: string; currentStock: number; averageDailySales: number
  coverageDays: number | null; suggestedQuantity: number; approvedQuantity: number
}
export interface ActionApproval { userName: string; decision: string; reason: string | null; createdAt: string }
export interface ActionDetail {
  id: string; type: string; status: ActionStatus; storeId: string; storeName: string; justification: string | null
  createdBy: string; createdByName: string; createdViaAi: boolean; conversationId: string | null
  createdAt: string; updatedAt: string; executedAt: string | null; failureReason: string | null
  items: ActionItem[]; approvals: ActionApproval[]; nextStatuses: ActionStatus[]
}

export interface AuditEventDto {
  id: string; userId: string | null; eventType: string; entityType: string | null; entityId: string | null
  details: string | null; requestId: string | null; createdAt: string
}

export interface Conversation { id: string; title: string; createdAt: string }
export interface ToolTrace { tool: string; status: string; durationMs: number; arguments: string }
export interface ChatMessage { id: string; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string; toolCalls: ToolTrace[] }
export interface AssistantReply { messageId: string; content: string; toolCalls: ToolTrace[] }
export interface AiStatus { configured: boolean; provider: string | null; model: string | null; message: string }
