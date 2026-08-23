import { useEffect, useState } from 'react';
import { getGroupBalances, computeSettlement, markSettlementPaid, getGroupMembers } from '../api/groups';

export default function SettlementView({ groupId, baseCurrency }) {
    const [balances, setBalances] = useState(null);
    const [members, setMembers] = useState([]);
    const [settlement, setSettlement] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [paying, setPaying] = useState(null);

    async function loadBalances() {
        try {
            setLoading(true);
            setError(null);
            const [b, memberResult] = await Promise.all([
                getGroupBalances(groupId),
                getGroupMembers(groupId)
            ]);
            const memberList = memberResult && Array.isArray(memberResult.members) ? memberResult.members : [];
            setBalances(b || []);
            setMembers(memberList);

            if (!b || b.length === 0 || b.every(item => Number(item.balance) === 0)) {
                setSettlement([]);
                return;
            }

            const plan = await computeSettlement(groupId, b || []);
            setSettlement(plan || []);
        } catch (e) {
            setError('Could not load balances');
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        setSettlement(null);
        loadBalances();
    }, [groupId]);

    async function onCompute() {
        try {
            setLoading(true);
            setError(null);
            const plan = await computeSettlement(groupId, balances || []);
            setSettlement(plan || []);
        } catch (e) {
            setError('Failed to compute settlement');
        } finally {
            setLoading(false);
        }
    }

    async function onMarkPaid(fromUserId, toUserId) {
        try {
            setPaying(`${fromUserId}-${toUserId}`);
            setError(null);
            await markSettlementPaid(groupId, fromUserId, toUserId);
            await loadBalances();
        } catch (e) {
            setError('Failed to mark settlement as paid');
        } finally {
            setPaying(null);
        }
    }

    if (loading) return <p>Loading…</p>;
    if (error) return <p className="error">{error}</p>;

    if (!balances) return <p>Loading balances…</p>;

    const allZero = balances.every(b => Number(b.balance) === 0);
    if (allZero) return <p className="empty">Everyone is settled up.</p>;

    const memberLookup = new Map(members.map(member => [member.userId, member.username]));

    return (
        <div>
            <button onClick={onCompute}>Generate settlement plan</button>
            {settlement && (
                <div>
                    {settlement.length === 0 ? (
                        <p className="empty">Everyone is settled up.</p>
                    ) : (
                        <ul>
                            {settlement.map((s, idx) => {
                                const fromUsername = memberLookup.get(s.fromUserId) || `User ${s.fromUserId}`;
                                const toUsername = memberLookup.get(s.toUserId) || `User ${s.toUserId}`;

                                return (
                                    <li key={idx}>
                                        <span>{`${fromUsername} pays ${toUsername}: ${baseCurrency} ${parseFloat(s.amount).toFixed(2)}`}</span>
                                        <button
                                            type="button"
                                            onClick={() => onMarkPaid(s.fromUserId, s.toUserId)}
                                            disabled={paying === `${s.fromUserId}-${s.toUserId}`}
                                            style={{ marginLeft: '0.75rem' }}
                                        >
                                            {paying === `${s.fromUserId}-${s.toUserId}` ? 'Marking paid…' : 'Mark as paid'}
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}