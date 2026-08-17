import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import GroupPage from '../src/pages/GroupPage.jsx';
import { getGroup } from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1']}>
            <Routes>
                <Route path="/groups/:id" element={<GroupPage />} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC2: a new group lists no expenses and shows zero balances', async () => {
    getGroup.mockResolvedValue({
        id: 1,
        name: 'Flat 3',
        description: null,
        baseCurrency: 'NZD',
        createdAt: '2026-08-16T00:00:00Z',
        memberCount: 1,
    });

    renderPage();

    expect(await screen.findByText('Flat 3')).toBeInTheDocument();
    expect(screen.getByText('No expenses yet.')).toBeInTheDocument();
    expect(screen.getByText(/NZD 0\.00/)).toBeInTheDocument();
});

it('AC8: shows a not-found message when the user is not a member', async () => {
    getGroup.mockResolvedValue(null);

    renderPage();

    expect(await screen.findByText('Group not found')).toBeInTheDocument();
    expect(screen.queryByText('No expenses yet.')).not.toBeInTheDocument();
});