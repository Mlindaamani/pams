/** Non-catalog line awaiting procurement review. */
export interface NonCatalogReviewLine {
  id: string;
  requestId: string;
  requestNumber?: string;
  requesterName?: string;
  departmentName?: string;
  description?: string;
  unit?: string;
  unitPrice?: number;
  notes?: string;
  createdAt?: string;
}
