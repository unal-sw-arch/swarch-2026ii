export interface ApiFieldErrors {
  [field: string]: string[] | string;
}

export interface ApiErrorResponse {
  detail?: string;
  [key: string]: unknown;
}

export interface PaginationState {
  page: number;
  rowsPerPage: number;
}
