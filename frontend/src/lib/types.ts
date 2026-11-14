export interface Account {
  id: number;
  name: string;
  email: string;
  role: "CUSTOMER" | "PROVIDER";
}
export interface Session {
  account: Account | null;
  csrf: { headerName: string; token: string };
}
export interface Provider {
  providerId: number;
  name: string;
}
export interface Service {
  serviceId: number;
  providerId: number;
  name: string;
  providerName: string;
  durationMinutes: number;
}
export interface Catalog {
  providers: Provider[];
  services: Service[];
}
export interface Slot {
  slotId: number;
  serviceId: number;
  providerId: number;
  providerName: string;
  serviceName: string;
  description: string;
  durationMinutes: number;
  startTime: string;
  endTime: string;
  status: string;
}
export interface SlotPage {
  items: Slot[];
  page: number;
  size: number;
  total: number;
}
export interface Appointment {
  appointmentId: number;
  userId: number;
  slotId: number;
  customerName: string;
  providerName: string;
  serviceName: string;
  startTime: string;
  endTime: string;
  status: "BOOKED" | "CANCELLED" | "COMPLETED";
}
export interface Turn {
  role: "user" | "assistant";
  text: string;
}
export interface Pending {
  token: string;
  action: string;
  summary: string;
  expiresAt: string;
  details: { service: string; tutor: string; time: string };
}
export interface Conversation {
  messages: Turn[];
  pending: Pending | null;
  appointmentId?: number;
  message?: string;
}
export interface Thread {
  id: number;
  title: string;
  updatedAt: string;
}
export interface HistoryPage {
  items: Thread[];
  page: number;
  hasNext: boolean;
}
export interface Transcript {
  thread: Thread;
  messages: Turn[];
}
