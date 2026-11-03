import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Dashboard } from '../../core/models/admin.models';
import { AdminDashboard } from './dashboard';

function today(offset = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const DATA: Dashboard = {
  today: { orders: 2, sales: 1545, averageOrder: 772.5 },
  last7Days: { orders: 3, sales: 2000, averageOrder: 666.67 },
  last30Days: { orders: 5, sales: 4000, averageOrder: 800 },
  toShip: 2, awaitingPayment: 1, paymentsNeedingReview: 1, lowStockSkus: 0, newMessages: 3, newCustomers30Days: 4, refunded30Days: 0,
  ordersByStatus: [{ status: 'CONFIRMED', count: 2 }],
  salesByDay: [{ day: today(0), orders: 2, sales: 1545 }, { day: today(3), orders: 1, sales: 455 }],
  topProducts: [{ productId: 1, name: 'Essential Crew Neck Tee', quantity: 4, sales: 1796 }],
  recentOrders: [],
};

describe('AdminDashboard', () => {
  it('shows sales, work waiting and a 14-day chart with a table alternative', async () => {
    TestBed.configureTestingModule({ imports: [AdminDashboard], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(AdminDashboard);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/admin/dashboard').flush(DATA);
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect([...el.querySelectorAll('.kpi strong')].map((s) => s.textContent?.trim())).toEqual(['₹1,545', '₹2,000', '₹4,000']);
    expect(el.querySelector('.tile.alert')?.textContent).toContain('Payments to review');
    const bars = el.querySelectorAll<HTMLElement>('.plot .bar');
    expect(bars.length).toBe(14);
    expect(bars[13].style.height).toBe('100%'); // today is the highest day
    expect(bars[12].style.height).toBe('0%');
    expect(el.querySelectorAll('.as-table tbody tr').length).toBe(14);
    expect(el.textContent).toContain('Essential Crew Neck Tee');
  });
});
