import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReceiptForm } from './ReceiptForm';

test('submits payer, numeric amount and currency, then clears', async () => {
  const onSubmit = vi.fn().mockResolvedValue(true);
  render(<ReceiptForm onSubmit={onSubmit} loading={false} />);
  fireEvent.change(screen.getByLabelText(/payer/i), { target: { value: 'Asha' } });
  fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '500' } });
  fireEvent.click(screen.getByRole('button', { name: /create receipt/i }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ payer: 'Asha', amount: 500, currency: 'INR' }));
  await waitFor(() => expect(screen.getByLabelText(/payer/i)).toHaveValue(''));
});

test('disables the button while loading', () => {
  render(<ReceiptForm onSubmit={vi.fn()} loading={true} />);
  expect(screen.getByRole('button')).toBeDisabled();
});
